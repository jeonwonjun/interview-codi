import type { CSSProperties, ReactNode } from 'react'
import styles from './Reveal.module.css'
import { MAX_TEXT_MS, PHRASE_STEP_MS, phrasesOf } from './revealText'

/** 같은 그릇 안의 내용을 {@code delay}ms 뒤에 아래 4px에서 올라오며 보여 준다. 그릇(카드)이 먼저, 내용이 나중에 온다. */
export function Reveal({ delay = 0, children }: { delay?: number; children: ReactNode }) {
  return (
    <div className={styles.reveal} style={{ animationDelay: `${delay}ms` } as CSSProperties}>
      {children}
    </div>
  )
}

/**
 * 말하듯 몇 어절씩 이어서 나타나는 글(knoted.kr의 답변 표시). 한 글자씩 타자 치는 효과는 읽는 속도를 강제하므로 쓰지 않는다.
 * 덩어리는 같은 문단 안의 인라인 요소라 화면 읽기 프로그램은 한 문장으로 읽는다.
 */
export function RevealText({ text, delay = 0 }: { text: string; delay?: number }) {
  const phrases = phrasesOf(text)
  const step = phrases.length === 0 ? 0 : Math.min(PHRASE_STEP_MS, MAX_TEXT_MS / phrases.length)
  return (
    <>
      {phrases.map((phrase, index) => (
        <span
          key={index}
          className={styles.phrase}
          style={{ animationDelay: `${Math.round(delay + index * step)}ms` } as CSSProperties}
        >
          {phrase}
          {index < phrases.length - 1 && ' '}
        </span>
      ))}
    </>
  )
}
