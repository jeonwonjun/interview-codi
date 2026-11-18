import type { ReactNode } from 'react'
import styles from './Badge.module.css'

/** 질문 번호·상태 같은 짧은 표시. 색만으로 뜻을 전하지 않도록 항상 글자를 담는다. */
export function Badge({ tone = 'primary', children }: { tone?: 'primary' | 'neutral'; children: ReactNode }) {
  return <span className={`${styles.badge} ${styles[tone]}`}>{children}</span>
}
