import type { ReactNode } from 'react'
import styles from './Badge.module.css'

type BadgeProps = {
  tone?: 'primary' | 'neutral'
  /** 처음 나타날 때 톡 튀어나온다. 값이 바뀌는 배지(몇 번째 답변 등)에 key와 함께 쓴다. */
  pop?: boolean
  children: ReactNode
}

/** 질문 번호·상태 같은 짧은 표시. 색만으로 뜻을 전하지 않도록 항상 글자를 담는다. */
export function Badge({ tone = 'primary', pop = false, children }: BadgeProps) {
  return <span className={[styles.badge, styles[tone], pop && styles.pop].filter(Boolean).join(' ')}>{children}</span>
}
