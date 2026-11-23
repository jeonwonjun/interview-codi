package io.interviewcodi.backend.eval;

import io.interviewcodi.backend.practice.client.ClovaSpeechClient;
import io.interviewcodi.backend.practice.client.OpenAiTranscriptionClient;
import io.interviewcodi.backend.practice.client.RtzrSpeechClient;
import io.interviewcodi.backend.practice.client.SpeechToTextClient;
import io.interviewcodi.backend.practice.client.SpeechToTextClient.AudioFile;
import io.interviewcodi.backend.practice.client.SpeechToTextClient.Transcription;
import io.interviewcodi.backend.practice.client.SpeechToTextException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 음성인식 제공자를 같은 녹음으로 비교한다(음성 답변 설계 "정한 것 1"). {@code ./gradlew sttEval}로만 돈다.
 * <p>
 * 녹음은 저장소 루트 {@code eval-audio/}(Git 제외)의 {@code {file}.*}, 정답 글은 {@code stt-scripts-v1.json}이다.
 * 녹음할 때 잘못 읽은 곳이 있으면 {@code eval-audio/{file}.txt}에 실제로 읽은 글을 두면 그것을 정답으로 쓴다.
 * <p>
 * 실제 호출 전에는 계획(녹음 유무·길이·키 유무·최대 비용)만 출력한다. {@code -Deval.execute=true}가 있어야 호출한다.
 * 키는 환경 변수나 {@code .env}에서 읽고 출력하지 않는다. 결과는 {@code build/reports/stt-eval/}에 남긴다.
 */
@Tag("stt-eval")
class SpeechToTextEval {

    /** 원/달러 환산(가정). 국내 제공자 비용을 달러로 함께 보이려고 쓴다. */
    private static final double KRW_PER_USD = 1_400;
    /**
     * 분당 달러. OpenAI는 가격표(2026-11-23). 리턴제로는 시간당 1,000원(부가세 별도, 파일당 최소 10초, 가입 시 600분 무료),
     * CLOVA는 시간당 약 1,200원(비교표 기준, 공식 가격표는 확인 전)이라 가정한다.
     */
    private static final Map<String, Double> USD_PER_MINUTE = Map.of(
            "openai", 0.0045,
            "rtzr", 1_000 / 60.0 / KRW_PER_USD,
            "clova", 1_200 / 60.0 / KRW_PER_USD);
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "m4a", "audio/mp4", "mp4", "audio/mp4", "webm", "audio/webm", "mp3", "audio/mpeg",
            "wav", "audio/wav", "ogg", "audio/ogg", "flac", "audio/flac", "aac", "audio/aac");

    private static final List<String> PROVIDERS = Arrays.stream(
            System.getProperty("eval.stt", "openai,rtzr,clova").split(","))
            .map(String::strip).filter(name -> !name.isEmpty()).toList();
    private static final boolean EXECUTE = Boolean.parseBoolean(System.getProperty("eval.execute", "false"));
    private static final String OPENAI_MODEL = System.getProperty("eval.openaiModel", "gpt-transcribe");
    private static final String RTZR_MODEL = System.getProperty("eval.rtzrModel", "sommers");
    private static final int LIMIT = Integer.parseInt(System.getProperty("eval.limit", "0"));
    // 같은 대본을 다른 조건(음질 등)으로 만든 녹음을 평가할 때 -Deval.audioDir로 폴더를 바꾼다.
    private static final Path AUDIO_DIR = Path.of(System.getProperty("eval.audioDir", "../eval-audio"));

    record Script(String id, String file, String device, String environment, String question, String script,
                  Map<String, List<String>> aliases) {
    }

    record Sample(Script script, Path audio, String reference, Double seconds) {
    }

    record Row(Sample sample, Transcription transcription, CharacterErrorRate.Result cer, String error) {
    }

    @Test
    @DisplayName("음성인식 제공자 비교")
    void evaluate() throws Exception {
        List<Sample> samples = loadSamples();
        printPlan(samples);
        if (!EXECUTE) {
            System.out.println("계획만 출력했어요. 실제로 호출하려면 -Deval.execute=true를 붙이세요.");
            return;
        }
        List<Sample> ready = samples.stream().filter(sample -> sample.audio() != null).toList();
        if (ready.isEmpty()) {
            throw new IllegalStateException("eval-audio/에 녹음이 없습니다.");
        }
        Path reportDir = Path.of("build/reports/stt-eval");
        Files.createDirectories(reportDir);
        StringBuilder summary = new StringBuilder("| 제공자 | 성공 | 평균 CER | 조용함 / 소음 | 노트북 / 휴대폰 "
                + "| 시간 p50 / p95 / 최대 | 비용 |\n| --- | --- | --- | --- | --- | --- | --- |\n");
        for (String provider : PROVIDERS) {
            SpeechToTextClient client = clientFor(provider);
            List<Row> rows = new ArrayList<>();
            for (Sample sample : ready) {
                rows.add(run(client, sample));
            }
            Files.writeString(reportDir.resolve(provider + ".md"), report(client.name(), rows));
            summary.append(summaryRow(provider, client.name(), rows));
        }
        Files.writeString(reportDir.resolve("summary.md"), summary);
        System.out.println(summary);
        System.out.println("자세한 결과: build/reports/stt-eval/");
    }

    private static Row run(SpeechToTextClient client, Sample sample) throws IOException {
        String extension = extension(sample.audio());
        AudioFile audio = new AudioFile(Files.readAllBytes(sample.audio()), sample.audio().getFileName().toString(),
                CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"));
        try {
            Transcription transcription = client.transcribe(audio);
            CharacterErrorRate.Result cer = CharacterErrorRate.of(sample.reference(), transcription.text(),
                    sample.script().aliases());
            System.out.printf(Locale.ROOT, "%s %s CER %.1f%% %dms%n", client.name(), sample.script().file(),
                    cer.rate() * 100, transcription.latencyMillis());
            return new Row(sample, transcription, cer, null);
        } catch (SpeechToTextException ex) {
            System.out.printf("%s %s 실패 %s%n", client.name(), sample.script().file(), ex.reason());
            return new Row(sample, null, null, ex.reason());
        }
    }

    private static void printPlan(List<Sample> samples) throws IOException {
        System.out.println("== 음성인식 평가 계획 ==");
        double minutes = 0;
        for (Sample sample : samples) {
            String state = sample.audio() == null ? "녹음 없음"
                    : sample.audio().getFileName() + (sample.seconds() == null ? " (길이 모름)"
                    : String.format(Locale.ROOT, " %.0f초", sample.seconds()));
            System.out.printf("- %s: %s%s%n", sample.script().file(), state,
                    Files.exists(AUDIO_DIR.resolve(sample.script().file() + ".txt")) ? ", 정답 글 교정본 사용" : "");
            if (sample.audio() != null) {
                // 길이를 모르면 넉넉히 2분으로 잡는다.
                minutes += sample.seconds() == null ? 2 : Math.max(sample.seconds(), 10) / 60.0;
            }
        }
        System.out.printf(Locale.ROOT, "녹음 %d개, 합 약 %.1f분%n",
                samples.stream().filter(sample -> sample.audio() != null).count(), minutes);
        for (String provider : PROVIDERS) {
            double usd = minutes * USD_PER_MINUTE.getOrDefault(provider, 0.02);
            System.out.printf(Locale.ROOT, "- %s: 키 %s, 최대 비용 약 $%.3f (약 %.0f원)%n", provider,
                    keysPresent(provider) ? "있음" : "없음", usd, usd * KRW_PER_USD);
        }
    }

    private static String report(String name, List<Row> rows) {
        StringBuilder text = new StringBuilder("# " + name + " (" + LocalDate.now() + ")\n\n");
        text.append("| 녹음 | 환경 | CER | 편집 거리 / 정답 글자 | 시간 | 길이 |\n| --- | --- | --- | --- | --- | --- |\n");
        for (Row row : rows) {
            Script script = row.sample().script();
            if (row.error() != null) {
                text.append("| %s | %s·%s | 실패 %s |  |  |  |%n".formatted(script.file(), script.device(),
                        script.environment(), row.error()));
                continue;
            }
            text.append(String.format(Locale.ROOT, "| %s | %s·%s | %.1f%% | %d / %d | %dms | %s |%n", script.file(),
                    script.device(), script.environment(), row.cer().rate() * 100, row.cer().distance(),
                    row.cer().referenceLength(), row.transcription().latencyMillis(),
                    row.transcription().audioSeconds() == null ? "" : "%.0f초".formatted(
                            row.transcription().audioSeconds())));
        }
        text.append("\n## 인식 결과와 정답\n\n숫자 표기(100개 / 백 개)와 군말 처리 차이는 CER에 오류로 남는다. 여기서 사람이 읽고 판정한다.\n");
        for (Row row : rows) {
            text.append("\n### ").append(row.sample().script().file()).append("\n\n");
            text.append("- 정답: ").append(row.sample().reference()).append('\n');
            text.append("- 인식: ").append(row.transcription() == null ? "(실패)" : row.transcription().text())
                    .append('\n');
        }
        return text.toString();
    }

    private static String summaryRow(String provider, String name, List<Row> rows) {
        List<Row> ok = rows.stream().filter(row -> row.error() == null).toList();
        List<Long> latencies = ok.stream().map(row -> row.transcription().latencyMillis()).sorted().toList();
        double minutes = rows.stream().mapToDouble(row -> row.sample().seconds() != null
                ? Math.max(row.sample().seconds(), 10) / 60.0
                : row.transcription() != null && row.transcription().audioSeconds() != null
                        ? row.transcription().audioSeconds() / 60.0 : 1).sum();
        return String.format(Locale.ROOT, "| %s | %d / %d | %.1f%% | %.1f%% / %.1f%% | %.1f%% / %.1f%% "
                        + "| %d / %d / %d ms | $%.4f |%n", name, ok.size(), rows.size(), mean(ok, row -> true),
                mean(ok, row -> row.sample().script().environment().equals("quiet")),
                mean(ok, row -> row.sample().script().environment().equals("noise")),
                mean(ok, row -> row.sample().script().device().equals("laptop")),
                mean(ok, row -> row.sample().script().device().equals("phone")),
                percentile(latencies, 0.5), percentile(latencies, 0.95),
                latencies.isEmpty() ? 0 : latencies.getLast(),
                minutes * USD_PER_MINUTE.getOrDefault(provider, 0.0));
    }

    /** 녹음별 CER의 평균(긴 녹음에 가중치를 주지 않는다). */
    private static double mean(List<Row> rows, java.util.function.Predicate<Row> filter) {
        return rows.stream().filter(filter).mapToDouble(row -> row.cer().rate() * 100).average().orElse(Double.NaN);
    }

    private static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        return sorted.get((int) Math.min(sorted.size() - 1, Math.ceil(p * sorted.size()) - 1));
    }

    private static SpeechToTextClient clientFor(String provider) throws IOException {
        return switch (provider) {
            case "openai" -> new OpenAiTranscriptionClient(loadKey("OPENAI_API_KEY"), OPENAI_MODEL,
                    URI.create("https://api.openai.com"), Duration.ofSeconds(60));
            case "rtzr" -> new RtzrSpeechClient(loadKey("RTZR_CLIENT_ID"), loadKey("RTZR_CLIENT_SECRET"), RTZR_MODEL,
                    URI.create("https://openapi.vito.ai"), Duration.ofSeconds(1), Duration.ofSeconds(120));
            case "clova" -> new ClovaSpeechClient(URI.create(loadKey("CLOVA_SPEECH_INVOKE_URL")),
                    loadKey("CLOVA_SPEECH_SECRET"), Duration.ofSeconds(60));
            default -> throw new IllegalArgumentException("모르는 제공자: " + provider);
        };
    }

    private static boolean keysPresent(String provider) throws IOException {
        List<String> names = switch (provider) {
            case "openai" -> List.of("OPENAI_API_KEY");
            case "rtzr" -> List.of("RTZR_CLIENT_ID", "RTZR_CLIENT_SECRET");
            case "clova" -> List.of("CLOVA_SPEECH_INVOKE_URL", "CLOVA_SPEECH_SECRET");
            default -> List.of("?");
        };
        for (String name : names) {
            if (findKey(name).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<Sample> loadSamples() throws IOException {
        List<Script> scripts;
        try (InputStream in = SpeechToTextEval.class.getResourceAsStream("/eval/stt-scripts-v1.json")) {
            scripts = JsonMapper.builder().build().readValue(in, new TypeReference<List<Script>>() {
            });
        }
        if (LIMIT > 0) {
            scripts = scripts.subList(0, Math.min(LIMIT, scripts.size()));
        }
        List<Sample> samples = new ArrayList<>();
        for (Script script : scripts) {
            Path audio = findAudio(script.file());
            Path correction = AUDIO_DIR.resolve(script.file() + ".txt");
            String reference = Files.exists(correction) ? Files.readString(correction).strip() : script.script();
            samples.add(new Sample(script, audio, reference, audio == null ? null : seconds(audio)));
        }
        return samples;
    }

    private static Path findAudio(String file) throws IOException {
        if (!Files.isDirectory(AUDIO_DIR)) {
            return null;
        }
        try (Stream<Path> files = Files.list(AUDIO_DIR)) {
            return files.filter(path -> path.getFileName().toString().startsWith(file + "."))
                    .filter(path -> CONTENT_TYPES.containsKey(extension(path)))
                    .findFirst().orElse(null);
        }
    }

    /** macOS의 afinfo로 녹음 길이를 잰다. 다른 환경에서는 null(제공자가 알려 준 길이를 쓴다). */
    private static Double seconds(Path audio) {
        try {
            Process process = new ProcessBuilder("afinfo", audio.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor(10, TimeUnit.SECONDS);
            Map<String, String> fields = new LinkedHashMap<>();
            for (String line : output.split("\n")) {
                if (line.contains("estimated duration:")) {
                    fields.put("duration", line.replaceAll("[^0-9.]", ""));
                }
            }
            return fields.containsKey("duration") ? Double.parseDouble(fields.get("duration")) : null;
        } catch (IOException | NumberFormatException ex) {
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String loadKey(String name) throws IOException {
        return findKey(name).orElseThrow(() -> new IllegalStateException(name + "가 없습니다."));
    }

    /** 환경 변수, 없으면 저장소 루트·backend 폴더의 .env에서 키만 읽는다. 값은 출력하지 않는다. */
    private static Optional<String> findKey(String name) throws IOException {
        String fromEnv = System.getenv(name);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Optional.of(fromEnv.strip());
        }
        for (Path path : List.of(Path.of("../.env"), Path.of(".env"))) {
            if (!Files.exists(path)) {
                continue;
            }
            for (String line : Files.readAllLines(path)) {
                if (line.startsWith(name + "=") && line.length() > name.length() + 1) {
                    return Optional.of(line.substring(name.length() + 1).strip());
                }
            }
        }
        return Optional.empty();
    }
}
