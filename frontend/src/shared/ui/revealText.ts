/** 어절 두 개씩 묶어 한 덩어리로 보여 준다. 덩어리 사이 간격(ms). */
export const PHRASE_STEP_MS = 45
/** 글이 길어도 이 시간 안에 다 나타나게 간격을 줄인다. */
export const MAX_TEXT_MS = 1_200

export function phrasesOf(text: string): string[] {
  const words = text.split(/\s+/).filter(Boolean)
  const phrases: string[] = []
  for (let i = 0; i < words.length; i += 2) {
    phrases.push(words.slice(i, i + 2).join(' '))
  }
  return phrases
}

/** RevealText가 {@code text}를 다 보여 주는 데 걸리는 시간. 다음 내용의 시작 시각을 맞출 때 쓴다. */
export function revealTextDuration(text: string): number {
  const count = phrasesOf(text).length
  return Math.min(count * PHRASE_STEP_MS, MAX_TEXT_MS)
}
