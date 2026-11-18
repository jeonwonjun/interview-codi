import type { ReactNode } from 'react'
import styles from './Pending.module.css'
import { PulseDots } from './PulseDot'

/** 피드백·다음 질문처럼 서버가 만드는 중인 것을 기다릴 때. 화면 읽기 프로그램에도 상태 문구로 알린다. */
export function Pending({ children }: { children: ReactNode }) {
  return (
    <div className={styles.pending} role="status">
      <span className={styles.dots}>
        <PulseDots />
      </span>
      <span key={String(children)} className={styles.message}>
        {children}
      </span>
    </div>
  )
}
