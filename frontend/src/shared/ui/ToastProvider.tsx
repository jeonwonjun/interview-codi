import { type ReactNode, useCallback, useEffect, useRef, useState } from 'react'
import { type ToastTone, ToastContext } from './toastContext'
import styles from './Toast.module.css'

const VISIBLE_MS = 3_000
const LEAVE_MS = 200

type Toast = { id: number; message: string; tone: ToastTone; leaving: boolean }

/**
 * 화면 아래쪽 가운데에 알림을 하나씩 띄운다. 새 알림이 오면 이전 알림을 바로 바꾼다.
 * DesignByWani 스낵바: 아래에서 올라오며(ease-out) 들어오고, 내려가며(ease-in) 나간다.
 * info는 role="status"(기다렸다 읽음), error는 role="alert"(바로 읽음)로 화면 읽기 프로그램에 알린다.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<Toast | null>(null)
  const nextId = useRef(0)

  const show = useCallback((message: string, tone: ToastTone = 'info') => {
    nextId.current += 1
    setToast({ id: nextId.current, message, tone, leaving: false })
  }, [])

  useEffect(() => {
    if (!toast) {
      return
    }
    const timer = toast.leaving
      ? window.setTimeout(() => setToast(null), LEAVE_MS)
      : window.setTimeout(() => setToast((current) => current && { ...current, leaving: true }), VISIBLE_MS)
    return () => window.clearTimeout(timer)
  }, [toast])

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className={styles.region}>
        {toast && (
          <div
            key={toast.id}
            className={[styles.toast, styles[toast.tone], toast.leaving && styles.leaving].filter(Boolean).join(' ')}
            role={toast.tone === 'error' ? 'alert' : 'status'}
          >
            {toast.tone === 'success' && (
              <svg className={styles.check} viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                <path d="m5 12.5 4.5 4.5L19 7.5" pathLength={1} />
              </svg>
            )}
            {toast.message}
          </div>
        )}
      </div>
    </ToastContext.Provider>
  )
}
