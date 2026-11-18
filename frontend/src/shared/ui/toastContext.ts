import { createContext, useContext } from 'react'

export type ToastTone = 'info' | 'error'

export type ShowToast = (message: string, tone?: ToastTone) => void

export const ToastContext = createContext<ShowToast | null>(null)

/** 짧은 알림을 띄운다. 3초 뒤 스스로 사라지므로 사용자가 꼭 읽어야 하는 오류에는 ErrorBanner를 쓴다. */
export function useToast(): ShowToast {
  const show = useContext(ToastContext)
  if (!show) {
    throw new Error('useToast는 ToastProvider 안에서만 쓸 수 있어요.')
  }
  return show
}
