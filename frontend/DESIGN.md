---
version: alpha
name: interview-codi
description: 면접코디의 시각 언어. 주니어 개발자가 자기 프로젝트 경험을 말로 설명하는 연습을 돕는 차분한 코칭형 화면이다. 옅은 슬레이트 바탕 위에 얇은 테두리의 흰 카드, 딥 틸 행동 버튼 하나를 쓴다. 긴장한 사용자가 질문을 읽고 답하고 피드백을 읽는 동안 마음이 놓이고, 녹음과 피드백 기록이 정돈돼 보이는 것이 우선이다.

colors:
  brand: "#0d9488"
  brand-hover: "#0f766e"
  brand-soft: "#f0fdfa"
  brand-text: "#0f766e"
  on-brand: "#ffffff"
  surface: "#ffffff"
  surface-subtle: "#f8fafc"
  border: "#e2e8f0"
  text: "#18181b"
  text-secondary: "#52525b"
  text-muted: "#a1a1aa"
  alert: "#e11d48"
  alert-soft: "#fff1f2"
  good: "#059669"
  good-soft: "#ecfdf5"
  improve: "#d97706"
  improve-soft: "#fffbeb"
  focus: "#0d9488"
  kakao: "#fee500"
  on-kakao: "#191919"

typography:
  display:
    fontFamily: LINE Seed KR, Pretendard Variable, Pretendard, system-ui, sans-serif
    fontSize: 26px
    fontSizeWeb: 32px
    fontWeight: 700
    lineHeight: 1.35
    lineHeightWeb: 1.3
    letterSpacing: -0.02em
  question:
    fontFamily: LINE Seed KR, Pretendard Variable, Pretendard, system-ui, sans-serif
    fontSize: 20px
    fontSizeWeb: 24px
    fontWeight: 700
    lineHeight: 1.5
    letterSpacing: -0.02em
  title:
    fontFamily: Pretendard Variable, Pretendard, -apple-system, system-ui, sans-serif
    fontSize: 18px
    fontSizeWeb: 20px
    fontWeight: 700
    lineHeight: 1.45
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Pretendard Variable, Pretendard, -apple-system, system-ui, sans-serif
    fontSize: 17px
    fontWeight: 400
    lineHeight: 1.7
    lineHeightWeb: 1.75
  body:
    fontFamily: Pretendard Variable, Pretendard, -apple-system, system-ui, sans-serif
    fontSize: 15px
    fontSizeWeb: 16px
    fontWeight: 400
    lineHeight: 1.65
  body-strong:
    fontFamily: Pretendard Variable, Pretendard, -apple-system, system-ui, sans-serif
    fontSize: 15px
    fontSizeWeb: 16px
    fontWeight: 600
    lineHeight: 1.65
  button:
    fontFamily: Pretendard Variable, Pretendard, -apple-system, system-ui, sans-serif
    fontSize: 16px
    fontWeight: 600
    lineHeight: 1.5
  button-sm:
    fontFamily: Pretendard Variable, Pretendard, -apple-system, system-ui, sans-serif
    fontSize: 15px
    fontWeight: 600
    lineHeight: 1.5
  caption:
    fontFamily: Pretendard Variable, Pretendard, -apple-system, system-ui, sans-serif
    fontSize: 13px
    fontWeight: 500
    lineHeight: 1.5

rounded:
  control: 8px
  card: 12px
  pill: 9999px

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
    backgroundColor: "{colors.surface-subtle}"
    textColor: "{colors.text}"
    maxWidth: 720px
    padding: "{spacing.2xl} {spacing.lg}"
  nav-bar:
    backgroundColor: "{colors.surface-subtle}"
    textColor: "{colors.text}"
    height: 64px
  button-primary:
    backgroundColor: "{colors.brand}"
    hoverBackgroundColor: "{colors.brand-hover}"
    textColor: "{colors.on-brand}"
    rounded: "{rounded.control}"
    padding: "{spacing.md} {spacing.xl}"
    height: 48px
  button-secondary:
    backgroundColor: "{colors.surface}"
    borderColor: "{colors.border}"
    hoverBackgroundColor: "{colors.surface-subtle}"
    textColor: "{colors.text}"
    rounded: "{rounded.control}"
    padding: "{spacing.md} {spacing.xl}"
    height: 48px
  button-ghost:
    backgroundColor: transparent
    hoverBackgroundColor: "{colors.border}"
    textColor: "{colors.text-secondary}"
    rounded: "{rounded.control}"
    padding: "{spacing.sm} {spacing.lg}"
  button-kakao:
    backgroundColor: "{colors.kakao}"
    textColor: "{colors.on-kakao}"
    rounded: "{rounded.card}"
    height: 52px
  card:
    backgroundColor: "{colors.surface}"
    borderColor: "{colors.border}"
    textColor: "{colors.text}"
    rounded: "{rounded.card}"
    padding: "{spacing.xl}"
  question-card:
    backgroundColor: "{colors.surface}"
    borderColor: "{colors.border}"
    textColor: "{colors.text}"
    rounded: "{rounded.card}"
    padding: "{spacing.2xl}"
  answer-input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    placeholderColor: "{colors.text-muted}"
    borderColor: "{colors.border}"
    focusBorderColor: "{colors.focus}"
    rounded: "{rounded.control}"
    padding: "{spacing.lg}"
  feedback-card:
    backgroundColor: "{colors.surface}"
    borderColor: "{colors.border}"
    textColor: "{colors.text}"
    rounded: "{rounded.card}"
    padding: "{spacing.xl}"
  improve-box:
    backgroundColor: "{colors.improve-soft}"
    labelColor: "{colors.improve}"
    textColor: "{colors.text}"
    rounded: "{rounded.card}"
    padding: "{spacing.lg} {spacing.xl}"
  good-box:
    backgroundColor: "{colors.good-soft}"
    labelColor: "{colors.good}"
    textColor: "{colors.text}"
    rounded: "{rounded.card}"
    padding: "{spacing.lg} {spacing.xl}"
  summary-box:
    backgroundColor: "{colors.brand-soft}"
    textColor: "{colors.text}"
    rounded: "{rounded.card}"
    padding: "{spacing.lg} {spacing.xl}"
  badge:
    backgroundColor: "{colors.brand-soft}"
    textColor: "{colors.brand-text}"
    rounded: "{rounded.pill}"
    padding: "{spacing.xs} {spacing.md}"
  recording-indicator:
    backgroundColor: "{colors.alert-soft}"
    textColor: "{colors.alert}"
    rounded: "{rounded.pill}"
  error-banner:
    backgroundColor: "{colors.alert-soft}"
    textColor: "{colors.alert}"
    rounded: "{rounded.card}"
    padding: "{spacing.md} {spacing.lg}"
---

## Overview

면접코디는 취업 준비를 시작한 주니어 개발자가 자기 프로젝트 경험을 말로 설명하도록 돕는 연습 도구다. 사용자는 질문을 읽고, 1분 정도 답하고, 피드백을 읽고, 다시 답한다. 면접 연습은 긴장되는 일이므로 화면은 **차분한 코치**처럼 보여야 한다. 시험지나 채점표처럼 보이면 안 된다.

- 핵심 무드: 깔끔함, 심리적 안정감, 정돈된 기록 관리, 차분한 전문성.
- 옅은 슬레이트 바탕(`{colors.surface-subtle}`) 위에 흰 카드(`{colors.surface}`)를 올리고, 1px 테두리(`{colors.border}`)로 층을 나눈다. 그림자는 떠 있는 요소에만 아주 연하게 쓴다.
- 행동 색은 딥 틸 `{colors.brand}` 하나다. 주요 버튼, 활성 탭, 핵심 강조에만 쓴다.
- 녹음 데이터와 AI 피드백은 의미 색 상자(강점·보완·요약)로 나눠 구조가 한눈에 보이게 한다.
- 모서리: 큰 카드·피드백 상자 12px, 버튼·입력 8px, 녹음 버튼·상태 배지는 완전한 원형.
- 여백은 넉넉하게(카드 안쪽 24px, 모바일 20px) 둔다.

## 참고한 것

- **Wise**(getdesign.md 분석): 옅은 바탕 + 흰 카드로 층을 만드는 방식, 48px 버튼 높이.
- **Airbnb**(getdesign.md 분석): 제목을 과하게 키우거나 굵게 하지 않는 절제.
- **knoted.kr**: 움직임 수치(Motion 절 참고).
- 이들 브랜드의 색·글꼴·로고는 쓰지 않는다. 딥 틸 팔레트는 이 서비스를 위해 정한 값이다.

## Colors

토큰 이름은 `tokens.css`의 `--color-*`와 같다. 대비는 WCAG 2.x 상대 휘도로 계산했다.

### 브랜드
- **Brand** `#0d9488`: 주요 버튼 배경, 활성 탭, 핵심 강조, 포커스 링. 글자는 흰색(`{colors.on-brand}`).
- **Brand hover** `#0f766e`: 버튼 호버와 누름.
- **Brand soft** `#f0fdfa`: AI 피드백 요약 상자 배경, 선택된 카드 강조, 배지 바탕.
- **Brand text** `#0f766e`: 흰 바탕·Brand soft 위의 작은 브랜드 글자(링크, 배지, 알맞음 안내). `#0d9488`은 흰 바탕에서 3.74:1이라 작은 글자에 쓰지 않는다.

### 바탕·테두리
- **Surface** `#ffffff`: 카드, 모달, 입력란.
- **Surface subtle** `#f8fafc`: 페이지 바탕, 보조 영역, 보조 버튼 호버.
- **Border** `#e2e8f0`: 카드·입력란 1px 테두리, 구분선, ghost 버튼 호버 바탕.

### 글자
- **Text** `#18181b`: 제목과 본문(피드백 본문 포함).
- **Text secondary** `#52525b`: 날짜, 타이머, 부가 설명. 흰 바탕 7.7:1, 바탕색 7.4:1.
- **Text muted** `#a1a1aa`: 플레이스홀더 전용(2.6:1). 읽어야 하는 글자에는 쓰지 않는다.

### 기능 의미
| 의미 | 글자·아이콘 | 바탕 | 쓰임 |
| --- | --- | --- | --- |
| Recording / Alert | `#e11d48` | `#fff1f2` | 녹음 중 상태, 시스템 오류, 되돌릴 수 없는 동작 확인 |
| Good | `#059669` | `#ecfdf5` | 답변 강점 포인트 |
| Improve | `#d97706` | `#fffbeb` | 답변 보완 포인트("다음에 바꿔 볼 한 가지") |

- 의미 색은 **이름표·아이콘·테두리**에 쓰고, 상자 안 본문은 `{colors.text}`로 쓴다. 의미 색을 제 바탕 위 글자로 쓰면 Improve 3.1:1, Good 3.6:1, Alert 4.3:1로 4.5:1에 못 미치기 때문이다. 이름표는 굵게(600) 쓰고 아이콘과 함께 둔다.
- **답변 피드백에 Alert 빨강을 쓰지 않는다.** 피드백은 잘못이 아니라 다음 시도의 방향이다. 보완점은 Improve 노랑으로 쓴다.
- **Kakao** `#fee500` / `#191919`: 카카오 로그인 버튼 전용. 카카오 디자인 가이드를 따른다.

## Typography

두 글꼴을 쓴다. 둘 다 [눈누](https://noonnu.cc)에서 웹 사용이 가능한 무료 글꼴로 확인했다.

- **LINE Seed KR Bold**(SIL OFL 1.1, LINE Corporation): 화면 제목·면접 질문·브랜드 이름에만 쓴다. 둥근 획이 옆에서 말을 거는 코치 느낌을 준다. 굵기 하나만 받는다.
- **Pretendard**(SIL OFL): 답변·피드백처럼 오래 읽는 글과 버튼·안내 등 나머지 모두. 화면에 나온 글자 조각만 받는다.

고른 과정(2026-11-19, 같은 질문·피드백 문구로 비교): Wanted Sans·수트는 Pretendard와 구별이 거의 안 됐고, 나눔스퀘어 네오는 폭이 넓어 질문이 한 줄 더 꺾였다. 카카오 큰글씨는 굵기 하나가 약 850KB로 LINE Seed(약 510KB)보다 무거웠다.

| 단계 | 앱(기본) | 웹(1024px 이상) | 굵기 | 줄 간격 | 쓰임 |
| --- | --- | --- | --- | --- | --- |
| `display` | 26px | 32px | 700, LINE Seed | 1.35 / 1.3 | 화면 제목, 첫 화면 인사 |
| `question` | 20px | 24px | 700, LINE Seed | 1.5 | 면접 질문. 화면에서 가장 먼저 읽혀야 한다 |
| `title` | 18px | 20px | 700 | 1.45 | 카드·확인 창·빈 화면 제목 |
| `body-lg` | 17px | 17px | 400 | 1.7 / 1.75 | 답변 입력, 피드백, 소개 문단(읽는 글) |
| `body` | 15px | 16px | 400 | 1.65 | 기본 안내 글(조작하는 글) |
| `body-strong` | 15px | 16px | 600 | 1.65 | 강조한 안내 글 |
| `button` / `button-sm` | 16px / 15px | 같음 | 600 | 1.5 | 버튼 md / sm, 상단 메뉴 |
| `caption` | 13px | 13px | 500 | 1.5 | 배지, 글자 수, 날짜, 입력란 이름 |

- CSS는 `font: var(--type-…)` 한 줄로 쓴다. 웹 값은 `tokens.css`의 1024px 미디어 쿼리가 바꾼다. 화면 파일에 px 글자 크기를 직접 쓰지 않는다.
- `font`는 `font-variant-numeric`을 되돌리므로 숫자 정렬(`tabular-nums`)은 `font` 다음 줄에 쓴다.
- 캡션(13px)보다 작게 쓰지 않는다. 앱 하단 탭 이름만 12px까지 허용한다.
- 자간: `display`·`question`은 -0.02em, `title`은 -0.01em. 한국어 본문에는 음수 자간을 주지 않는다.
- 비용: LINE Seed는 파일 하나(실측 499KB), Pretendard는 첫 화면 기준 조각 10개 합 28KB. LINE Seed가 오는 동안 제목은 Pretendard로 먼저 보인다(`font-display: swap`). 줄이려면 글자 조각 나누기(OFL 수정본이라 이름을 바꿔야 하는지 확인 필요)를 검토한다.

## Layout

- 4px 단위 간격. 토큰은 위 `spacing`과 `--space-card-mobile`(20px).
- 연습 화면은 가운데 정렬 한 열, 최대 폭 720px. 읽고 쓰는 화면이라 넓히지 않는다.
- 카드 안쪽 여백 24px. 모바일(< 768px)에서는 좌우 여백 16px, 카드 안쪽 여백 20px로 줄인다.
- 한 화면의 주요 버튼(primary)은 하나다. 나머지는 secondary·ghost.

## Shape

| 토큰 | 값 | 쓰임 |
| --- | --- | --- |
| `--radius-card` | 12px | 카드, 피드백·요약 상자, 모달, 토스트, 오류 배너, 카카오 버튼 |
| `--radius-control` | 8px | 버튼, 입력란, 상단 메뉴 링크, 아이콘 버튼, 목록 안 작은 링크 |
| `--radius-pill` | 9999px | 녹음 버튼, 상태 배지, 단계 번호, 점 |

## Icons

[lucide-react](https://lucide.dev)(ISC) 1.68.0을 버전 고정으로 쓴다. 24px 격자, 둥근 끝·둥근 꺾임이라 둥근 LINE Seed 제목과 어울린다.

참고: knoted.kr(실측: 1.5px 둥근 선 SVG, 켜진 상태는 같은 아이콘을 채워서 표시, 탭 알약이 0.24s 미끄러짐). knoted의 채팅·녹음 버튼은 그 서비스가 canvas로 직접 그린 손그림이라 모양과 점묘 효과는 가져오지 않고 방식만 참고한다. 다른 아이콘 세트(예: Lineicons)는 라이선스를 확인하고 Lucide에 없는 것만, 선 두께와 끝 모양을 맞춰 쓴다.

- 크기: 버튼 md 18px, 버튼 sm·배지 14~16px, 아이콘 버튼 18px(누를 영역 40px), 빈 화면 28px(64px 원 안).
- 선 굵기: 크기와 상관없이 화면에서 **1.75px**. `main.tsx`의 `LucideProvider`(`strokeWidth={1.75}`, `absoluteStrokeWidth`)가 정하므로 아이콘마다 `strokeWidth`를 쓰지 않는다.
- **켜진 상태는 채운 아이콘**: 같은 아이콘을 빈 선(꺼짐·기다림) ↔ 채움(켜짐·도착)으로 바꿔 상태를 보여 준다. 채울 때는 `fill="currentColor"`. 안쪽에 선이 있는 아이콘(`MessageSquareText` 등)은 채우면 선이 사라지므로 바깥선만 있는 아이콘(`MessageSquare`, `Mic`)을 쓴다. 바뀌는 순간 0.6 → 1로 톡(400ms, spring). 색만으로 알리지 않고 글자를 함께 둔다.
- 색은 글자색을 따른다(`currentColor`). 아이콘만으로 색 의미를 주지 않는다.
- **아이콘만**: 뜻이 분명할 때만 `IconButton`으로 쓰고 `label`(화면 읽기 이름 + 말풍선)을 반드시 단다. 삭제(`Trash2`, 확인 창을 거친다), 로그아웃(`LogOut`), 펼치기·이동 화살표(`ChevronRight`), 닫기(`X`).
- **아이콘 + 글자**: 그 밖의 행동은 글자를 남기고 앞에 아이콘을 둔다. 아이콘은 화면 읽기 프로그램에 숨긴다(`aria-hidden`).

| 쓰는 곳 | 아이콘 |
| --- | --- |
| 상단 홈·기록 탭 | 아이콘 없이 글자만(NavTabs) |
| 피드백 이름표 | `MessageSquare`: 기다림·실패는 빈 선, 도착하면 채움 |
| 자료 없이 바로 연습 / 이력서로 맞춤 연습 | `MessageSquareText` / `FileText` |
| 이어진 질문 배지 | `CornerDownRight` |
| 답변 고치기 | `Pencil` |
| 이어서 질문 받기 | `MessageCirclePlus` |
| 다시 답하기, 피드백 다시 요청 | `RotateCcw` |
| 질문 넘기기 / 연습 마치기 | `SkipForward` / `Flag` |
| improve-box("다음에 바꿔 볼 한 가지") | `Lightbulb` |
| good-box(잘한 점) | `ThumbsUp` |
| recording-indicator, 말로 답하기 | `Mic`: 대기는 빈 선, 녹음 중은 `alert` 색으로 채움 (멈춤 `Square`) |
| 빈 기록 | `CalendarDays` |
| 완료 알림 | 직접 그린 체크(선이 그려지는 움직임 때문에 lucide `Check` 대신) |

## Elevation

| 단계 | 처리 | 쓰임 |
| --- | --- | --- |
| 0 | 테두리·그림자 없음 | 페이지 바탕 위 글, 상자 안 상자 |
| 1 | 1px `{colors.border}` 테두리 | 카드, 입력란, 보조 버튼, 중립 배지 |
| 2 | 테두리 + `0 1px 3px rgba(0, 0, 0, 0.05)` | 모달, 토스트, 호버한 기록 항목처럼 떠 있는 것만 |

- 모달 뒤 어두운 막은 `rgba(24, 24, 27, 0.4)`.

## Components

- **button-primary**: 딥 틸, 흰 글자, 모서리 8px, 높이 48px. "답변 제출", "피드백 받기", "다음 질문"처럼 그 화면에서 다음에 할 일 하나.
- **button-secondary**: 흰 바탕 + 1px 테두리. "다시 답하기", "질문 넘기기".
- **button-ghost**: 배경 없는 `text-secondary` 글자 버튼, 호버 시 `border` 바탕. "연습 종료", "로그아웃".
- **button-kakao**: 카카오 가이드에 맞춘 노란 버튼(모서리 12px, 말풍선 심볼 + "카카오 로그인").
- **question-card**: 흰 카드에 `question` 글자. 위에 "질문 1" 같은 배지를 둔다.
- **answer-input**: 흰 바탕, 1px 테두리, 플레이스홀더 `text-muted`, 포커스 시 `focus` 2px 링. 약 1분 분량 안내는 아래 캡션으로 두고 글자 수 제한처럼 막지 않는다.
- **feedback-card**: 흰 카드. 관찰을 짧게 쓰고, 마지막에 **improve-box** 하나("다음에 바꿔 볼 한 가지"). 강점을 따로 보여 줄 때는 **good-box**, 여러 답변을 묶은 요약은 **summary-box**(`brand-soft`).
- **recording-indicator**: 녹음 중임을 알리는 원형 배지·버튼. `alert` 점 + "녹음 중" 글자, `alert-soft` 바탕.
- **error-banner**: 시스템 오류 전용. 무엇을 하면 되는지 함께 쓴다("다시 로그인해 주세요").

## Motion

움직임은 상태가 바뀌는 순간에만 쓴다. 질문·답변·피드백을 오래 읽는 화면이므로 장식으로 움직이지 않는다.
참고: knoted.kr(실측: 장면 등장 700ms `cubic-bezier(0.2, 0.8, 0.2, 1)`, 카드 강조 800ms, 선택 칩 250ms, 녹음 점 1.4s, 답변은 어절 묶음 단위로 표시), DesignByWani의 UI별 애니메이션 정리본(스낵바·팝업·좋아요·체크박스).

### 토큰

| 토큰 | 값 | 쓰임 |
| --- | --- | --- |
| `--ease-out` | `cubic-bezier(0.2, 0.8, 0.2, 1)` | 화면에 들어오는 움직임. 빠르게 출발해 오래 멈추며 안착 |
| `--ease-in` | `cubic-bezier(0.4, 0, 1, 1)` | 화면에서 나가는 움직임 |
| `--ease-in-out` | `cubic-bezier(0.65, 0, 0.35, 1)` | 상태 사이를 오가는 움직임(스위치·체크) |
| `--duration-fast` | 150ms | 호버·누름·포커스 |
| `--duration-base` | 250ms | 토스트·작은 요소 등장, 나가는 움직임 |
| `--duration-enter` | 600ms | 카드·피드백·새 질문 등장 |
| `--stagger` | 80ms | 같은 그릇 안의 내용을 순서대로 보여 줄 때 간격 |
| `--ease-spring` | `cubic-bezier(0.34, 1.56, 0.64, 1)` | 살짝 넘쳤다 돌아옴. 값이 바뀐 배지에만 |
| `--duration-slow` | 800ms | 색이 천천히 스며드는 강조 |

- 움직이는 양은 작게: 투명도와 4~16px 이동, 0.96 → 1 크기 변화까지만. 배지 톡(0.6 → 1)만 예외.
- 한 글자씩 타자 치는 효과는 쓰지 않는다. 읽는 속도를 화면이 정하게 되기 때문이다. 글은 어절 두 개씩 묶어 45ms 간격, 길어도 1.2초 안에 다 보인다.
- 들어올 때 `--ease-out`, 나갈 때 `--ease-in`. 나가는 움직임은 들어오는 움직임보다 짧게.
- `prefers-reduced-motion: reduce`이면 이동·크기 변화를 없애고 150ms 투명도 변화만 남긴다. 반복 애니메이션(숨쉬는 점)은 멈춘다.

### 화면에서 쓰는 곳

| 순간 | 움직임 |
| --- | --- |
| 피드백 도착 | 카드가 먼저 올라오고(600ms), 관찰·지난번 개선점이 어절 두 개씩 이어서 나타남(투명도 + 흐림 2px → 0, 400ms). 바꿔 볼 한 가지는 마지막에 올라오며 바탕색 → 노란색이 800ms에 걸쳐 스며듦. 기다리던 화면에서만 움직이고, 새로 연 화면은 바로 보여 줌 |
| 피드백·다음 질문 기다림 | 점 세 개가 160ms씩 엇갈려 숨쉼(1.4s, 투명도 1 ↔ 0.35, 크기 1 ↔ 0.8). "오래 걸리고 있어요"로 문구가 바뀌면 아래 4px에서 바꿔 끼움 |
| 다음 질문으로 넘어감 | 새 질문 카드가 아래 8px에서 올라오며 나타남(600ms) |
| 다시 답함 | "N번째 답변" 배지가 톡 튀어나옴(0.6 → 1, 400ms, spring) |
| 답변 쓰는 중 | 글자 수가 1분 분량(300~500자)에 들어오면 안내가 `brand-text`로 바뀜(250ms). 막거나 평가하지 않음 |
| 버튼 로딩 | 글자가 흐려지고(150ms) 같은 자리에 점 세 개. 폭은 그대로 |
| 스낵바(토스트) | 아래 16px에서 올라옴(250ms, ease-out), 3초 뒤 8px 내려가며 사라짐(200ms, ease-in). 끝난 일(success)은 체크 선이 그려짐(400ms, 150ms 뒤) |
| 확인 창(Dialog) | 들어올 때 배경이 어두워지고(250ms) 창이 0.96 → 1. 나갈 때 150ms ease-in으로 0.98까지 줄며 사라짐 |
| 접고 펴기(지난 질문) | 화살표가 90° 돌고(250ms) 내용이 위 4px에서 내려옴 |
| 기록 목록 호버 | 그림자가 떠오르고(250ms) 화살표가 3px 앞으로 |
| 버튼 누름 | 0.98로 살짝 작아짐(150ms) |
| 상단 탭 이동 | 선택 알약(`brand`)이 다음 탭 자리로 미끄러지며 폭을 맞춤(250ms, ease-out), 글자색은 같은 시간에 흰색으로. 처음 그릴 때는 미끄러지지 않고 제자리. 맞는 탭이 없는 화면(연습 상세)에서는 그 자리에서 흐려짐 |
| 피드백 도착 아이콘 | 이름표의 빈 말풍선이 채워진 말풍선으로 바뀌며 톡(0.6 → 1, 400ms, spring) |

## Component states

| 컴포넌트 | 변형 | 상태 |
| --- | --- | --- |
| Button | primary · secondary · ghost · danger / md(48px) · sm(40px) / block | 기본 · 호버 · 누름(0.98) · 포커스 링 · 비활성(투명도 0.55) · 로딩(폭 유지, 점 세 개, `aria-busy`) |
| TextArea | — | 기본 · 호버(`text-muted` 테두리) · 포커스(`focus` 2px 링) · 오류(`alert` 테두리 + 안내 문구) · 비활성. 아래 안내는 기본 · 알맞음(`brand-text`) |
| Card | default(흰색) · soft(바탕색) / reveal | 둘 다 1px 테두리. 등장할 때만 움직임 |
| Badge | primary(`brand-soft`) · neutral(`surface-subtle` + 테두리) / pop | 값이 바뀌면 톡(key와 함께) |
| Toast | info · success(Good 체크) · error | 등장 · 3초 유지 · 사라짐. `role="status"`(오류는 `alert`) |
| Dialog | default · danger(삭제 등 되돌릴 수 없는 동작) | 열림 · 닫히는 중(150ms, 입력 받지 않음) · 닫힘. Esc·배경 클릭으로 닫힘, 열리면 취소에 포커스, Tab은 창 안에서만, 닫히면 연 버튼으로 포커스 복귀 |
| PulseDot · PulseDots | 점 하나(녹음처럼 이어지는 상태) · 점 셋(만드는 중) | 반복(동작 줄이기면 멈춤). 옆에 상태 문구를 함께 둔다 |
| EmptyState | — | 제목 · 설명 · 행동 버튼(선택) |
| NavTabs | — | 기본(`text-secondary`) · 호버(`text`) · 선택(`brand` 알약 + 흰 글자, `aria-current="page"`) · 누름(0.96). 테두리 1px 흰 알약 안에 둔다 |

- `danger`는 삭제처럼 되돌릴 수 없는 동작의 확인 버튼에만 쓴다. 답변 피드백에는 쓰지 않는다(Colors의 기능 의미 규칙).

## Voice

- 존댓말, 짧은 문장. 코치가 옆에서 말하듯 쓴다.
- 점수·등급·합격 같은 평가 표현을 쓰지 않는다. 개선점은 하나만 제안한다.
- 칭찬은 근거가 있을 때만 한다("기술을 고른 이유를 먼저 말해서 듣기 쉬웠어요").
- 오류 문구는 탓하지 않고 다음 행동을 알려 준다.

## Accessibility

- 글자 대비 4.5:1 이상(큰 제목 3:1)을 목표로 한다.
- **알려진 예외**: primary 버튼의 흰 글자와 `brand` 바탕은 3.74:1이다. 브랜드 색을 고정 값으로 정해서 생긴 차이다. 버튼 글자는 16px 600으로 굵게 두고, 호버·누름은 5.47:1인 `brand-hover`로 바꾼다. 색을 바꿀 수 있게 되면 가장 먼저 다시 볼 곳이다.
- 모든 버튼·입력에 보이는 포커스 링(`{colors.focus}` 2px, 2px 간격). 흰 바탕 3.74:1, 페이지 바탕 3.58:1로 비텍스트 기준 3:1을 넘는다.
- 버튼 높이 48px 이상. 모바일 터치 대상도 같다.
- 색만으로 상태를 알리지 않는다. 배지·오류·의미 상자에는 글자(와 아이콘)를 함께 쓴다.
