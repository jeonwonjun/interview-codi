import { type ReactNode, useEffect, useId, useRef, useState } from 'react'
import { Button } from './Button'
import styles from './ConfirmDialog.module.css'

const LEAVE_MS = 150

type Props = {
  open: boolean
  title: string
  children?: ReactNode
  confirmLabel: string
  cancelLabel?: string
  /** 되돌릴 수 없는 동작이면 확인 버튼을 danger로 보여 준다. */
  tone?: 'default' | 'danger'
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/**
 * 확인 대화상자. 열리면 취소 버튼에 포커스를 두고(실수로 Enter를 눌러도 지우지 않도록), Tab은 대화상자 안에서만 돈다.
 * Esc·바깥 클릭은 취소이고, 닫히면 연 요소로 포커스를 돌려준다.
 * DesignByWani 팝업: 배경이 어두워지며 창이 커지며(ease-out) 들어오고, 더 짧게(ease-in) 나간다.
 */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  cancelLabel = '취소',
  tone = 'default',
  busy = false,
  onConfirm,
  onCancel,
}: Props) {
  // 닫힐 때 바로 없애지 않고 나가는 움직임(150ms)을 보여 준 뒤 없앤다.
  const [rendered, setRendered] = useState(open)
  const [leaving, setLeaving] = useState(false)
  if (open && (!rendered || leaving)) {
    setRendered(true)
    setLeaving(false)
  } else if (!open && rendered && !leaving) {
    setLeaving(true)
  }
  useEffect(() => {
    if (!leaving) {
      return
    }
    const timer = window.setTimeout(() => {
      setRendered(false)
      setLeaving(false)
    }, LEAVE_MS)
    return () => window.clearTimeout(timer)
  }, [leaving])

  const titleId = useId()
  const descriptionId = useId()
  const boxRef = useRef<HTMLDivElement>(null)
  const cancelRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
    cancelRef.current?.focus()
    return () => opener?.focus()
  }, [open])

  if (!rendered) {
    return null
  }

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (leaving) {
      return
    }
    if (event.key === 'Escape') {
      event.stopPropagation()
      if (!busy) {
        onCancel()
      }
      return
    }
    if (event.key !== 'Tab') {
      return
    }
    const focusable = boxRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled)')
    if (!focusable || focusable.length === 0) {
      return
    }
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  return (
    <div
      className={[styles.backdrop, leaving && styles.leaving].filter(Boolean).join(' ')}
      onMouseDown={(event) => event.target === event.currentTarget && !busy && !leaving && onCancel()}
      onKeyDown={handleKeyDown}
    >
      <div
        ref={boxRef}
        className={styles.box}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={children ? descriptionId : undefined}
      >
        <h2 id={titleId} className={styles.title}>
          {title}
        </h2>
        {children && (
          <div id={descriptionId} className={styles.description}>
            {children}
          </div>
        )}
        <div className={styles.actions}>
          <Button ref={cancelRef} variant="secondary" onClick={onCancel} disabled={busy}>
            {cancelLabel}
          </Button>
          <Button variant={tone === 'danger' ? 'danger' : 'primary'} onClick={onConfirm} loading={busy}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}
