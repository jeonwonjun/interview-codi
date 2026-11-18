import type { HTMLAttributes } from 'react'
import styles from './Card.module.css'

type CardProps = HTMLAttributes<HTMLElement> & {
  tone?: 'default' | 'soft'
  /** 처음 나타날 때 아래에서 올라오며 나타난다(DESIGN.md Motion). key가 바뀌면 다시 나타난다. */
  reveal?: boolean
  as?: 'section' | 'div' | 'article'
}

/** 흰 카드(default) 또는 세이지 카드(soft). 바탕과의 색 차이로 층을 나누므로 그림자를 쓰지 않는다. */
export function Card({ tone = 'default', reveal = false, as: Tag = 'section', className, ...props }: CardProps) {
  const classes = [styles.card, styles[tone], reveal && styles.reveal, className].filter(Boolean).join(' ')
  return <Tag className={classes} {...props} />
}
