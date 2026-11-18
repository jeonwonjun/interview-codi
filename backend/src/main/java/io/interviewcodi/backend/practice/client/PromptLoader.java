package io.interviewcodi.backend.practice.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** {@code resources/prompts}의 프롬프트를 읽는다. 버전을 파일 이름에 두어 결과의 {@code generator}에 남긴다. */
final class PromptLoader {

    private PromptLoader() {
    }

    static String load(String name) {
        try (InputStream in = PromptLoader.class.getResourceAsStream("/prompts/" + name + ".md")) {
            if (in == null) {
                throw new IllegalStateException("Prompt not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
