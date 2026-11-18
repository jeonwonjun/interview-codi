import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AnswerForm } from './AnswerForm'

describe('AnswerForm', () => {
  it('1분 분량(300~500자)에 들어오면 안내 문구가 바뀌지만 제출을 막지는 않는다', async () => {
    render(<AnswerForm submitLabel="답변 제출" busy={false} onSubmit={async () => true} />)
    const textbox = screen.getByRole('textbox', { name: '내 답변' })

    await userEvent.type(textbox, '짧은 답변')
    expect(screen.getByText('5자 · 1분 분량은 300~500자')).toBeTruthy()
    expect(screen.getByRole('button', { name: '답변 제출' })).toHaveProperty('disabled', false)

    await userEvent.clear(textbox)
    await userEvent.click(textbox)
    await userEvent.paste('가'.repeat(320))
    expect(screen.getByText('320자 · 1분 분량이에요')).toBeTruthy()
  })
})
