import type { ComponentProps } from 'react'
import styles from './Button.module.css'
import { PulseDot } from './PulseDot'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
type Size = 'md' | 'sm'

type ButtonProps = ComponentProps<'button'> & {
  variant?: Variant
  size?: Size
  block?: boolean
  /** 요청 중. 누를 수 없고, 글자 대신 점을 보여 주되 버튼 폭은 그대로 둔다. */
  loading?: boolean
}

/**
 * DESIGN.md의 Button. 한 화면의 primary는 하나만 둔다. danger는 삭제처럼 되돌릴 수 없는 동작의 확인에만 쓴다.
 */
export function Button({
  variant = 'primary',
  size = 'md',
  block = false,
  loading = false,
  disabled,
  className,
  type = 'button',
  children,
  ...props
}: ButtonProps) {
  const classes = [styles.button, styles[variant], styles[size], block && styles.block, loading && styles.loading, className]
    .filter(Boolean)
    .join(' ')
  return (
    <button type={type} className={classes} disabled={disabled || loading} aria-busy={loading || undefined} {...props}>
      <span className={styles.label}>{children}</span>
      {loading && (
        <span className={styles.spinner}>
          <PulseDot />
        </span>
      )}
    </button>
  )
}
