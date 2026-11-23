package io.interviewcodi.backend.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CharacterErrorRateTest {

    @Test
    @DisplayName("띄어쓰기·문장부호·대소문자 차이는 오류로 세지 않는다")
    void ignoresSpacingAndPunctuation() {
        CharacterErrorRate.Result result = CharacterErrorRate.of("예약 기능을 맡았습니다.", "예약기능을 맡았습니다", Map.of());

        assertThat(result.distance()).isZero();
        assertThat(result.rate()).isZero();
    }

    @Test
    @DisplayName("바뀐 글자·빠진 글자를 정답 글자 수로 나눈다")
    void countsEdits() {
        CharacterErrorRate.Result result = CharacterErrorRate.of("유니크제약", "유니크재약", Map.of());

        assertThat(result.distance()).isEqualTo(1);
        assertThat(result.referenceLength()).isEqualTo(5);
        assertThat(result.rate()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("영어 기술 용어를 한글로 적어도 맞은 것으로 센다")
    void acceptsAliases() {
        Map<String, List<String>> aliases = Map.of(
                "GitHub Actions", List.of("깃허브 액션스"),
                "GitHub", List.of("깃허브"));

        CharacterErrorRate.Result result = CharacterErrorRate.of(
                "GitHub Actions와 GitHub 시크릿", "깃허브 액션스와 깃허브 시크릿", aliases);

        assertThat(result.distance()).isZero();
    }
}
