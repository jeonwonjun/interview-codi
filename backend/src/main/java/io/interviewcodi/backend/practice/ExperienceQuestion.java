package io.interviewcodi.backend.practice;

/**
 * 자료 없는 연습의 고정 질문. 사용자에 대해 아는 것이 없으므로 AI로 만들지 않는다.
 * 연습 안에서 아직 쓰지 않은 것을 선언 순서대로 낸다. {@code key}는 DB의 {@code preset_key}이므로 바꾸지 않는다.
 */
enum ExperienceQuestion {

    ROLE("role", "최근 프로젝트에서 본인이 맡은 기능 하나를 설명해 주세요."),
    HARDEST("hardest", "프로젝트를 하면서 가장 어려웠던 문제와 해결 과정을 말해 주세요."),
    TECH_CHOICE("tech-choice", "프로젝트에서 사용한 기술 하나를 고른 이유를 설명해 주세요."),
    TEAMWORK("teamwork", "팀에서 의견이 달랐던 경험이 있다면, 어떻게 정리했는지 말해 주세요."),
    IMPROVE("improve", "다시 만든다면 바꾸고 싶은 부분과 그 이유를 말해 주세요.");

    private final String key;
    private final String content;

    ExperienceQuestion(String key, String content) {
        this.key = key;
        this.content = content;
    }

    static ExperienceQuestion first() {
        return values()[0];
    }

    String key() {
        return key;
    }

    String content() {
        return content;
    }
}
