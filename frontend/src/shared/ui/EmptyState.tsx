import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import styles from './EmptyState.module.css'

/** 보여 줄 것이 없을 때. 무엇이 없는지와 다음에 할 일 하나를 함께 둔다. */
type EmptyStateProps = { icon?: LucideIcon; title: string; description?: string; action?: ReactNode }

export function EmptyState({ icon: Icon, title, description, action }: EmptyStateProps) {
  return (
    <div className={styles.empty}>
      {Icon && (
        <span className={styles.icon} aria-hidden="true">
          <Icon size={28} strokeWidth={1.75} />
        </span>
      )}
      <p className={styles.title}>{title}</p>
      {description && <p className={styles.description}>{description}</p>}
      {action && <div className={styles.action}>{action}</div>}
    </div>
  )
}
