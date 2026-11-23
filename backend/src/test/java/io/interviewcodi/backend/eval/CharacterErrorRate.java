package io.interviewcodi.backend.eval;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 글자 오류율(CER) = 편집 거리(바꿈 + 빠짐 + 더함) ÷ 정답 글자 수.
 * <p>
 * 띄어쓰기·문장부호·대소문자는 세지 않는다(한국어 인식기는 띄어쓰기가 제각각이라 내용 오류와 섞이면 비교가 흐려진다).
 * 영어 기술 용어는 한글로 적어도 맞은 것으로 센다: 인식 결과의 대체 표기(레디스)를 정답 표기(redis)로 바꾼 뒤 비교한다.
 * 숫자 표기 차이(100개 / 백 개)는 오류로 남는다. 보고서에서 사람이 따로 본다.
 */
final class CharacterErrorRate {

    private CharacterErrorRate() {
    }

    record Result(double rate, int distance, int referenceLength) {
    }

    static Result of(String reference, String hypothesis, Map<String, List<String>> aliases) {
        String ref = normalize(reference);
        String hyp = normalize(applyAliases(hypothesis, aliases));
        int distance = distance(ref, hyp);
        return new Result(ref.isEmpty() ? 0 : (double) distance / ref.length(), distance, ref.length());
    }

    /** 긴 대체 표기부터 바꿔서 "깃허브 액션스"가 "깃허브"로 먼저 바뀌지 않게 한다. */
    static String applyAliases(String text, Map<String, List<String>> aliases) {
        String result = text;
        List<Map.Entry<String, String>> pairs = aliases.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(alias -> Map.entry(alias, entry.getKey())))
                .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
                .toList();
        for (Map.Entry<String, String> pair : pairs) {
            result = result.replace(pair.getKey(), pair.getValue());
        }
        return result;
    }

    static String normalize(String text) {
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        StringBuilder kept = new StringBuilder();
        nfc.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(kept::appendCodePoint);
        return kept.toString();
    }

    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
