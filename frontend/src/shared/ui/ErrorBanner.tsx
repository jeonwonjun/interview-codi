import type { ReactNode } from 'react'
import styles from './ErrorBanner.module.css'

/** 시스템 오류 전용. 답변 피드백에는 쓰지 않는다(DESIGN.md). */
export function ErrorBanner({ children }: { children: ReactNode }) {
  return (
    <p className={styles.banner} role="alert">
      {children}
    </p>
  )
}
