---
version: alpha
name: interview-codi
description: 면접코디의 시각 언어. 주니어 개발자가 자기 프로젝트 경험을 말로 설명하는 연습을 돕는 친근한 코칭형 화면이다. 옅은 세이지 바탕 위에 둥근 흰 카드, 민트 그린 행동 버튼 하나, 차분한 굵기의 한국어 제목을 쓴다. 긴장한 사용자가 질문을 읽고 답하고 피드백을 읽는 동안 부담을 주지 않는 것이 우선이다.

colors:
  primary: "#5ed39a"
  primary-hover: "#4cc488"
  primary-pressed: "#3bb377"
  on-primary: "#13201a"
  primary-pale: "#e3f7ec"
  primary-ink: "#0f5a3a"
  ink: "#16211c"
  body: "#3f4a45"
  mute: "#626c67"
  hairline: "#dde3de"
  canvas: "#ffffff"
  canvas-soft: "#f1f5f0"
  canvas-strong: "#e6ece6"
  tip: "#fff1d6"
  tip-ink: "#6b4a00"
  negative: "#c8363c"
  negative-pale: "#fdecec"
  focus: "#2f80ed"
  kakao: "#fee500"
  on-kakao: "#191919"

typography:
  display-lg:
    fontFamily: Pretendard Variable, Pretendard, -apple-system, system-ui, sans-serif
    fontSize: 32px
    fontWeight: 700
    lineHeight: 1.35
    letterSpacing: -0.64px
  display-md:
    fontFamily: Pretendard Variable, Pretendard, system-ui, sans-serif
    fontSize: 26px
    fontWeight: 700
    lineHeight: 1.4
    letterSpacing: -0.52px
  title-lg:
    fontFamily: Pretendard Variable, Pretendard, system-ui, sans-serif
    fontSize: 20px
    fontWeight: 600
    lineHeight: 1.45
    letterSpacing: -0.2px
  title-md:
    fontFamily: Pretendard Variable, Pretendard, system-ui, sans-serif
    fontSize: 17px
    fontWeight: 600
    lineHeight: 1.5
  question:
    fontFamily: Pretendard Variable, Pretendard, system-ui, sans-serif
    fontSize: 22px
    fontWeight: 600
    lineHeight: 1.55
    letterSpacing: -0.22px
  body-lg:
    fontFamily: Pretendard Variable, Pretendard, system-ui, sans-serif
    fontSize: 17px
    fontWeight: 400
    lineHeight: 1.7
  body-md:
    fontFamily: Pretendard Variable, Pretendard, system-ui, sans-serif
    fontSize: 15px
    fontWeight: 400
    lineHeight: 1.65
  body-md-strong:
    fontFamily: Pretendard Variable, Pretendard, system-ui, sans-serif
    fontSize: 15px
    fontWeight: 600
    lineHeight: 1.65
  caption:
    fontFamily: Pretendard Variable, Pretendard, system-ui, sans-serif
    fontSize: 13px
    fontWeight: 500
    lineHeight: 1.5
  button:
    fontFamily: Pretendard Variable, Pretendard, system-ui, sans-serif
    fontSize: 16px
    fontWeight: 600
    lineHeight: 1.5

rounded:
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  full: 9999px

spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  2xl: 32px
  3xl: 48px
  4xl: 64px

components:
  page:
    backgroundColor: "{colors.canvas-soft}"
    textColor: "{colors.ink}"
    maxWidth: 720px
    padding: "{spacing.2xl} {spacing.lg}"
  nav-bar:
    backgroundColor: "{colors.canvas-soft}"
    textColor: "{colors.ink}"
    typography: "{typography.title-md}"
    height: 64px
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.full}"
    padding: "{spacing.md} {spacing.xl}"
    height: 48px
  button-secondary:
    backgroundColor: "{colors.canvas-strong}"
    textColor: "{colors.ink}"
    typography: "{typography.button}"
    rounded: "{rounded.full}"
    padding: "{spacing.md} {spacing.xl}"
    height: 48px
  button-ghost:
    backgroundColor: transparent
    textColor: "{colors.body}"
    typography: "{typography.button}"
    rounded: "{rounded.full}"
    padding: "{spacing.sm} {spacing.lg}"
  button-kakao:
    backgroundColor: "{colors.kakao}"
    textColor: "{colors.on-kakao}"
    typography: "{typography.button}"
    rounded: "{rounded.md}"
    height: 52px
  card:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    rounded: "{rounded.xl}"
    padding: "{spacing.xl}"
  question-card:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.question}"
    rounded: "{rounded.xl}"
    padding: "{spacing.2xl}"
  answer-input:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    borderColor: "{colors.hairline}"
    focusBorderColor: "{colors.primary-ink}"
    typography: "{typography.body-lg}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"
  feedback-card:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.body}"
    typography: "{typography.body-md}"
    rounded: "{rounded.xl}"
    padding: "{spacing.xl}"
  tip-card:
    backgroundColor: "{colors.tip}"
    textColor: "{colors.tip-ink}"
    typography: "{typography.body-md-strong}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg} {spacing.xl}"
  badge:
    backgroundColor: "{colors.primary-pale}"
    textColor: "{colors.primary-ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.full}"
    padding: "{spacing.xs} {spacing.md}"
  error-banner:
    backgroundColor: "{colors.negative-pale}"
    textColor: "{colors.negative}"
    typography: "{typography.body-md}"
    rounded: "{rounded.lg}"
    padding: "{spacing.md} {spacing.lg}"
---

## Overview

면접코디는 취업 준비를 시작한 주니어 개발자가 자기 프로젝트 경험을 말로 설명하도록 돕는 연습 도구다. 사용자는 질문을 읽고, 1분 정도 답하고, 피드백을 읽고, 다시 답한다. 면접 연습은 긴장되는 일이므로 화면은 **친근한 코치**처럼 보여야 한다. 시험지나 채점표처럼 보이면 안 된다.

- 옅은 세이지 바탕(`{colors.canvas-soft}`) 위에 흰 카드(`{colors.canvas}`)를 올린다. 그림자 대신 바탕과 카드의 색 차이로 층을 나눈다.
- 행동 색은 민트 그린 `{colors.primary}` 하나다. 화면마다 "다음에 할 일" 하나에만 쓴다.
- 모서리는 모두 둥글다. 카드는 24px, 버튼은 완전한 알약 모양이다.
- 제목은 굵지만 무겁지 않게(700 이하) 쓴다. 한국어는 900 같은 극단적 굵기에서 획이 뭉친다.
- 읽는 화면이므로 본문 줄 간격을 넉넉히(1.65~1.7) 둔다.

## 참고한 것

- **Wise**(getdesign.md 분석): 옅은 바탕 + 흰 카드로 층을 만드는 방식, 24px 카드 모서리, 밝은 행동 색 위에 어두운 글자, 48px 버튼 높이.
- **Airbnb**(getdesign.md 분석): 제목을 과하게 키우거나 굵게 하지 않는 절제, 사진·여백으로 따뜻함을 주는 태도.
- 두 브랜드의 색·글꼴·로고는 쓰지 않는다. 민트 그린, 세이지 바탕, Pretendard는 이 서비스를 위해 정한 값이다.

## Colors

### 행동
- **Primary** `#5ed39a`: 주요 버튼 배경. 글자는 항상 `{colors.on-primary}`(대비 약 9:1). 흰 글자를 올리지 않는다(대비 부족).
- **Primary hover / pressed** `#4cc488` / `#3bb377`.
- **Primary pale** `#e3f7ec`: 배지, 선택된 항목 배경.
- **Primary ink** `#0f5a3a`: 흰 바탕 위 초록 글자(링크, 입력 포커스 테두리).

### 바탕
- **Canvas soft** `#f1f5f0`: 페이지 바탕. 서비스의 기본 분위기.
- **Canvas** `#ffffff`: 카드와 입력란.
- **Canvas strong** `#e6ece6`: 보조 버튼, 눌림 상태.
- **Hairline** `#dde3de`: 입력란 테두리, 구분선.

### 글자
- **Ink** `#16211c`: 제목과 본문 기본.
- **Body** `#3f4a45`: 긴 피드백 본문.
- **Mute** `#626c67`: 보조 정보. 흰 바탕·세이지 바탕·보조 버튼 바탕 모두에서 4.5:1 이상이 되도록 정한 가장 옅은 글자색이다.

### 의미
- **Tip** `#fff1d6` / **Tip ink** `#6b4a00`: 피드백의 "다음에 바꿔 볼 한 가지". 따뜻한 노랑으로 눈에 띄되 경고처럼 보이지 않게 한다.
- **Negative** `#c8363c` / `#fdecec`: 시스템 오류(저장 실패, 로그인 실패)에만 쓴다. **답변 피드백에는 빨강을 쓰지 않는다.** 피드백은 잘못이 아니라 다음 시도의 방향이다.
- **Focus** `#2f80ed`: 키보드 포커스 링.
- **Kakao** `#fee500` / `#191919`: 카카오 로그인 버튼 전용. 카카오 디자인 가이드를 따른다.

## Typography

**Pretendard**(SIL OFL)를 쓴다. 한글과 영문이 함께 자연스럽고 굵기가 다양하다. 웹폰트를 못 받으면 시스템 글꼴로 떨어진다.

| 토큰 | 크기 | 굵기 | 줄 간격 | 쓰임 |
| --- | --- | --- | --- | --- |
| `display-lg` | 32px | 700 | 1.35 | 첫 화면 큰 제목 |
| `display-md` | 26px | 700 | 1.4 | 화면 제목 |
| `title-lg` | 20px | 600 | 1.45 | 카드 제목 |
| `title-md` | 17px | 600 | 1.5 | 작은 제목, 상단 바 |
| `question` | 22px | 600 | 1.55 | 면접 질문 본문. 화면에서 가장 먼저 읽혀야 한다 |
| `body-lg` | 17px | 400 | 1.7 | 답변 입력, 소개 문단 |
| `body-md` | 15px | 400 | 1.65 | 기본 본문, 피드백 |
| `caption` | 13px | 500 | 1.5 | 배지, 보조 설명 |
| `button` | 16px | 600 | 1.5 | 버튼 |

- 한국어 본문에는 음수 자간을 주지 않는다. 제목에만 살짝 준다.
- 숫자(시간, 회차)는 `font-variant-numeric: tabular-nums`.

## Layout

- 4px 단위 간격. 토큰은 위 `spacing`.
- 연습 화면은 가운데 정렬 한 열, 최대 폭 720px. 읽고 쓰는 화면이라 넓히지 않는다.
- 모바일(< 768px)에서는 좌우 여백 16px, 카드 안쪽 여백 20px로 줄인다.
- 한 화면의 주요 버튼(primary)은 하나다. 나머지는 secondary·ghost.

## Elevation

| 단계 | 처리 | 쓰임 |
| --- | --- | --- |
| 0 | 그림자·테두리 없음 | 바탕 위 카드(색 차이가 곧 층) |
| 1 | 1px `{colors.hairline}` 테두리 | 입력란, 흰 바탕 위 카드 |
| 2 | `0 8px 24px rgba(22, 33, 28, 0.08)` | 모달, 토스트처럼 떠 있는 것만 |

## Components

- **button-primary**: 민트 그린 알약, 높이 48px. "답변 제출", "피드백 받기", "다음 질문"처럼 그 화면에서 다음에 할 일 하나.
- **button-secondary**: 연한 회녹색 알약. "다시 답하기", "질문 넘기기".
- **button-ghost**: 배경 없는 글자 버튼. "연습 종료", "로그아웃".
- **button-kakao**: 카카오 가이드에 맞춘 노란 버튼(모서리 12px, 말풍선 심볼 + "카카오 로그인").
- **question-card**: 흰 카드에 `question` 글자. 위에 "질문 1" 같은 배지를 둔다.
- **answer-input**: 흰 바탕, 연한 테두리, 포커스 시 `primary-ink` 테두리. 약 1분 분량 안내는 아래 `caption`으로 두고 글자 수 제한처럼 막지 않는다.
- **feedback-card**: 흰 카드. 잘한 점 → 근거 → 순서를 짧게 쓰고, 마지막에 **tip-card** 하나("다음에 바꿔 볼 한 가지").
- **error-banner**: 시스템 오류 전용. 무엇을 하면 되는지 함께 쓴다("다시 로그인해 주세요").

## Voice

- 존댓말, 짧은 문장. 코치가 옆에서 말하듯 쓴다.
- 점수·등급·합격 같은 평가 표현을 쓰지 않는다. 개선점은 하나만 제안한다.
- 칭찬은 근거가 있을 때만 한다("기술을 고른 이유를 먼저 말해서 듣기 쉬웠어요").
- 오류 문구는 탓하지 않고 다음 행동을 알려 준다.

## Accessibility

- 글자 대비 4.5:1 이상(큰 제목 3:1). 위 색은 이 기준으로 정했다.
- 모든 버튼·입력에 보이는 포커스 링(`{colors.focus}` 2px, 2px 간격).
- 버튼 높이 48px 이상. 모바일 터치 대상도 같다.
- 색만으로 상태를 알리지 않는다. 배지·오류에는 글자를 함께 쓴다.
