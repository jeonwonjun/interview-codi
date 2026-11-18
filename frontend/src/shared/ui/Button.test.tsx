import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Button } from './Button'

describe('Button', () => {
  it('로딩 중에는 누를 수 없고 바쁨 상태를 알린다', async () => {
    const onClick = vi.fn()
    render(
      <Button loading onClick={onClick}>
        답변 제출
      </Button>,
    )

    const button = screen.getByRole('button', { name: '답변 제출' })
    await userEvent.click(button)

    expect(button).toHaveProperty('disabled', true)
    expect(button.getAttribute('aria-busy')).toBe('true')
    expect(onClick).not.toHaveBeenCalled()
  })
})
