import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { Button } from './Button'
import { ConfirmDialog } from './ConfirmDialog'

function Harness({ onConfirm = () => {} }: { onConfirm?: () => void }) {
  const [open, setOpen] = useState(false)
  return (
    <>
      <Button onClick={() => setOpen(true)}>삭제</Button>
      <ConfirmDialog
        open={open}
        title="연습 기록을 삭제할까요?"
        confirmLabel="삭제하기"
        tone="danger"
        onConfirm={onConfirm}
        onCancel={() => setOpen(false)}
      >
        다시 볼 수 없어요.
      </ConfirmDialog>
    </>
  )
}

describe('ConfirmDialog', () => {
  it('열리면 취소에 포커스를 두고, Esc로 닫으면 연 버튼으로 포커스를 돌려준다', async () => {
    render(<Harness />)
    const opener = screen.getByRole('button', { name: '삭제' })

    await userEvent.click(opener)
    expect(screen.getByRole('dialog', { name: '연습 기록을 삭제할까요?' })).toBeTruthy()
    expect(document.activeElement).toBe(screen.getByRole('button', { name: '취소' }))

    await userEvent.keyboard('{Escape}')
    expect(document.activeElement).toBe(opener)
    // 나가는 움직임(150ms) 뒤에 사라진다.
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  it('Tab은 대화상자 안에서만 돈다', async () => {
    render(<Harness />)
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))

    await userEvent.tab()
    expect(document.activeElement).toBe(screen.getByRole('button', { name: '삭제하기' }))
    await userEvent.tab()
    expect(document.activeElement).toBe(screen.getByRole('button', { name: '취소' }))
  })

  it('확인을 누르면 확인 동작을 부른다', async () => {
    const onConfirm = vi.fn()
    render(<Harness onConfirm={onConfirm} />)
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))

    await userEvent.click(screen.getByRole('button', { name: '삭제하기' }))

    expect(onConfirm).toHaveBeenCalledOnce()
  })
})
