import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeServer, ok } from '../../test/fakeServer'
import type { Practice, PracticeSummary } from '../practice/practiceApi'
import { HomePage } from './HomePage'

const question = {
  id: 10,
  orderNo: 1,
  content: '최근 프로젝트에서 본인이 맡은 기능 하나를 설명해 주세요.',
  sourceKind: 'EXPERIENCE_PRESET' as const,
  skipped: false,
  current: true,
  attempts: [],
}

function practice(id: number): Practice {
  return {
    id,
    startMode: 'EXPERIENCE',
    status: 'IN_PROGRESS',
    createdAt: '2026-11-19T10:00:00Z',
    completedAt: null,
    canProceed: true,
    readOnlyReason: null,
    questions: [question],
    followUpRequest: null,
  }
}

function summary(id: number, canProceed: boolean): PracticeSummary {
  return {
    id,
    startMode: 'EXPERIENCE',
    status: canProceed ? 'IN_PROGRESS' : 'COMPLETED',
    createdAt: '2026-11-19T10:00:00Z',
    completedAt: null,
    questionCount: 1,
    answerCount: 1,
    canProceed,
  }
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('첫 화면', () => {
  it('진행 중인 연습이 있으면 그 질문을 바로 보여 준다', async () => {
    fakeServer({
      'GET /api/practices': ok({ items: [summary(7, true), summary(3, false)] }),
      'GET /api/practices/7': ok(practice(7)),
    })
    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: question.content })).toBeTruthy()
    expect(screen.getByRole('textbox', { name: '내 답변' })).toBeTruthy()
  })

  it('진행 중인 연습이 없으면 시작 버튼을 보여 주고, 누르면 이 자리에서 첫 질문을 보여 준다', async () => {
    const server = fakeServer({
      'GET /api/practices': ok({ items: [summary(3, false)] }),
      'POST /api/practices': () => ({ status: 201, body: practice(8) }),
      'GET /api/practices/8': ok(practice(8)),
    })
    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    )

    await userEvent.click(await screen.findByRole('button', { name: '연습 시작하기' }))

    expect(await screen.findByRole('heading', { name: question.content })).toBeTruthy()
    expect(server.calls.filter((call) => call.key === 'POST /api/practices')).toHaveLength(1)
  })
})
