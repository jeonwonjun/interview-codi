import type { ReactNode } from 'react'
import styles from './EmptyState.module.css'

/** 보여 줄 것이 없을 때. 무엇이 없는지와 다음에 할 일 하나를 함께 둔다. */
export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className={styles.empty}>
      <p className={styles.title}>{title}</p>
      {description && <p className={styles.description}>{description}</p>}
      {action && <div className={styles.action}>{action}</div>}
    </div>
  )
}
