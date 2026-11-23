package io.interviewcodi.backend.practice.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 받는 녹음 형식. 브라우저 {@code MediaRecorder}는 Chrome·Firefox가 webm, Safari가 mp4를 만든다.
 * 브라우저가 붙인 형식만 믿지 않고 파일 앞부분의 서명도 확인해 오디오가 아닌 파일을 저장하지 않는다.
 */
public enum AudioFormat {

    WEBM("audio/webm", "webm") {
        @Override
        boolean matchesSignature(byte[] head) {
            // EBML 헤더
            return startsWith(head, 0, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});
        }
    },
    MP4("audio/mp4", "m4a") {
        @Override
        boolean matchesSignature(byte[] head) {
            return startsWith(head, 4, ascii("ftyp"));
        }
    },
    OGG("audio/ogg", "ogg") {
        @Override
        boolean matchesSignature(byte[] head) {
            return startsWith(head, 0, ascii("OggS"));
        }
    };

    private final String contentType;
    private final String extension;

    AudioFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    /**
     * 브라우저가 보낸 형식(예: {@code audio/webm;codecs=opus})과 파일 앞부분이 모두 맞을 때만 형식을 정한다.
     */
    public static Optional<AudioFormat> detect(String contentType, byte[] content) {
        if (contentType == null || content == null) {
            return Optional.empty();
        }
        String baseType = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(format -> format.contentType.equals(baseType) || format.aliases(baseType))
                .filter(format -> format.matchesSignature(content))
                .findFirst();
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    abstract boolean matchesSignature(byte[] head);

    /** Safari가 {@code audio/x-m4a}나 {@code video/mp4}로 보내는 경우가 있다. 저장은 {@code audio/mp4}로 한다. */
    boolean aliases(String baseType) {
        return this == MP4 && (baseType.equals("audio/x-m4a") || baseType.equals("video/mp4"));
    }

    private static boolean startsWith(byte[] content, int offset, byte[] signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        return Arrays.equals(content, offset, offset + signature.length, signature, 0, signature.length);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
