import type { CSSProperties } from 'react'
import styles from './PulseDot.module.css'

/** 숨쉬는 점 하나. 녹음 중처럼 계속 이어지는 상태에 쓴다. 스스로 상태를 알리지 않으므로 옆에 문구를 함께 둔다. */
export function PulseDot({ delay = 0 }: { delay?: number }) {
  return <span className={styles.dot} style={{ animationDelay: `${delay}ms` } as CSSProperties} aria-hidden="true" />
}

/** 점 세 개가 160ms씩 엇갈려 숨쉰다. 코치가 답을 쓰고 있는 것처럼 무언가를 만드는 중일 때 쓴다. */
export function PulseDots() {
  return (
    <span className={styles.dots} aria-hidden="true">
      <PulseDot />
      <PulseDot delay={160} />
      <PulseDot delay={320} />
    </span>
  )
}
