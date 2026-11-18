import type { CSSProperties, ReactNode } from 'react'
import styles from './Reveal.module.css'

/**
 * 같은 그릇 안의 내용을 순서대로 보여 준다. {@code index}번째는 그릇이 나타난 뒤 index × 80ms 늦게 나타난다.
 * knoted.kr처럼 그릇(카드)이 먼저, 내용이 나중에 온다.
 */
export function Reveal({ index = 0, children }: { index?: number; children: ReactNode }) {
  return (
    <div className={styles.reveal} style={{ '--reveal-index': index } as CSSProperties}>
      {children}
    </div>
  )
}
