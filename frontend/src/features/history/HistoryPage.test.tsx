import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fakeServer, ok } from '../../test/fakeServer'
import { ToastProvider } from '../../shared/ui/ToastProvider'
import type { PracticeSummary } from '../practice/practiceApi'
import { HistoryPage } from './HistoryPage'

function summary(id: number, createdAt: Date, answerCount: number): PracticeSummary {
  return {
    id,
    startMode: 'EXPERIENCE',
    status: 'COMPLETED',
    createdAt: createdAt.toISOString(),
    completedAt: null,
    questionCount: 2,
    answerCount,
    canProceed: false,
  }
}

beforeEach(() => {
  // 오늘을 2026년 11월 19일로 고정한다. 타이머는 실제로 흐르게 둔다(화면 갱신이 막히지 않게).
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 10, 19, 9, 0))
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

function renderPage() {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <HistoryPage />
      </ToastProvider>
    </MemoryRouter>,
  )
}

describe('달력 기록', () => {
  it('답한 날에만 연습 표시를 하고, 날짜를 누르면 그날 연습을 보여 준다', async () => {
    fakeServer({
      'GET /api/practices': ok({
        items: [
          summary(3, new Date(2026, 10, 18, 22, 47), 2),
          summary(2, new Date(2026, 10, 17, 8, 0), 0),
          summary(1, new Date(2026, 10, 16, 21, 0), 1),
        ],
      }),
    })
    renderPage()

    expect(await screen.findByRole('button', { name: '11월 18일, 연습 1번' })).toBeTruthy()
    // 시작만 한 날은 표시하지 않는다.
    expect(screen.getByRole('button', { name: '11월 17일, 연습 없음' })).toBeTruthy()
    // 오늘 기록이 없으면 가장 최근 연습한 날을 먼저 보여 준다.
    expect(screen.getByRole('heading', { name: '11월 18일 수요일' })).toBeTruthy()
    expect(screen.getByText('질문 2개 · 답변 2번')).toBeTruthy()

    await userEvent.click(screen.getByRole('button', { name: '11월 17일, 연습 없음' }))

    expect(screen.getByRole('heading', { name: '11월 17일 화요일' })).toBeTruthy()
    expect(screen.getByText('질문 2개 · 아직 답하지 않음')).toBeTruthy()
  })

  it('기록이 하나도 없으면 시작 안내를 보여 준다', async () => {
    fakeServer({ 'GET /api/practices': ok({ items: [] }) })
    renderPage()

    expect(await screen.findByText('아직 연습 기록이 없어요.')).toBeTruthy()
  })
})
