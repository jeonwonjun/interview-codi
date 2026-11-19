import { act, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useToast } from './toastContext'
import { ToastProvider } from './ToastProvider'

function Trigger({ tone }: { tone?: 'info' | 'error' }) {
  const toast = useToast()
  return <button onClick={() => toast('연습 기록을 삭제했어요.', tone)}>알림</button>
}

describe('ToastProvider', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('알림은 3초 뒤 사라진다', () => {
    vi.useFakeTimers()
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    )

    act(() => screen.getByRole('button', { name: '알림' }).click())
    expect(screen.getByRole('status').textContent).toBe('연습 기록을 삭제했어요.')

    act(() => vi.advanceTimersByTime(3_000))
    act(() => vi.advanceTimersByTime(200))
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('오류 알림은 바로 읽히도록 alert로 알린다', () => {
    render(
      <ToastProvider>
        <Trigger tone="error" />
      </ToastProvider>,
    )

    act(() => screen.getByRole('button', { name: '알림' }).click())

    expect(screen.getByRole('alert')).toBeTruthy()
  })
})
