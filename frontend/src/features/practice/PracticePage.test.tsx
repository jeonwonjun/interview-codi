import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeServer, ok } from '../../test/fakeServer'
import { PracticePage } from './PracticePage'
import type { AnswerAttempt, Practice } from './practiceApi'

const question = {
  id: 10,
  orderNo: 1,
  content: '최근 프로젝트에서 본인이 맡은 기능 하나를 설명해 주세요.',
  sourceKind: 'EXPERIENCE_PRESET' as const,
  skipped: false,
  current: true,
  attempts: [] as AnswerAttempt[],
}

function practiceWith(attempts: AnswerAttempt[]): Practice {
  return {
    id: 1,
    startMode: 'EXPERIENCE',
    status: 'IN_PROGRESS',
    createdAt: '2026-11-19T10:00:00Z',
    completedAt: null,
    canProceed: true,
    readOnlyReason: null,
    questions: [{ ...question, attempts }],
    followUpRequest: null,
  }
}

function attempt(overrides: Partial<AnswerAttempt> = {}): AnswerAttempt {
  return {
    id: 100,
    questionId: 10,
    attemptNo: 1,
    inputMode: 'TEXT',
    currentRevision: { id: 1000, revisionNo: 1, content: '예약 기능을 맡았습니다.' },
    feedback: null,
    feedbackRequest: { id: 500, status: 'PENDING' },
    recording: null,
    transcriptionRequest: null,
    ...overrides,
  }
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/practices/1']}>
      <Routes>
        <Route path="/practices/:practiceId" element={<PracticePage />} />
      </Routes>
    </MemoryRouter>,
  )
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('연습 화면', () => {
  it('답변을 제출하면 피드백을 만드는 중임을 보여 준다', async () => {
    const server = fakeServer({
      'GET /api/practices/1': [ok(practiceWith([])), ok(practiceWith([attempt()]))],
      'POST /api/questions/10/answers': () => ({ status: 201, body: attempt() }),
      'GET /api/ai-requests/500': ok({ id: 500, status: 'PENDING' }),
    })
    renderPage()

    await userEvent.type(await screen.findByLabelText('내 답변'), '예약 기능을 맡았습니다.')
    await userEvent.click(screen.getByRole('button', { name: '답변 제출' }))

    expect(await screen.findByText('피드백을 만들고 있어요.')).toBeTruthy()
    const submit = server.calls.find((call) => call.key === 'POST /api/questions/10/answers')!
    expect(submit.body).toMatchObject({ content: '예약 기능을 맡았습니다.' })
    expect((submit.body as { clientRequestId: string }).clientRequestId).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('제출이 네트워크 오류로 실패하면 입력을 지우지 않고, 다시 보낼 때 같은 clientRequestId를 쓴다', async () => {
    const server = fakeServer({
      'GET /api/practices/1': [ok(practiceWith([])), ok(practiceWith([])), ok(practiceWith([attempt()]))],
      'POST /api/questions/10/answers': [
        () => Promise.reject(new TypeError('Failed to fetch')),
        () => ({ status: 201, body: attempt() }),
      ],
      'GET /api/ai-requests/500': ok({ id: 500, status: 'PENDING' }),
    })
    renderPage()

    const textarea = await screen.findByLabelText('내 답변')
    await userEvent.type(textarea, '예약 기능을 맡았습니다.')
    await userEvent.click(screen.getByRole('button', { name: '답변 제출' }))

    expect(await screen.findByRole('alert')).toBeTruthy()
    expect((screen.getByLabelText('내 답변') as HTMLTextAreaElement).value).toBe('예약 기능을 맡았습니다.')

    await userEvent.click(screen.getByRole('button', { name: '답변 제출' }))
    await screen.findByText('피드백을 만들고 있어요.')
    const ids = server.calls
      .filter((call) => call.key === 'POST /api/questions/10/answers')
      .map((call) => (call.body as { clientRequestId: string }).clientRequestId)
    expect(ids).toHaveLength(2)
    expect(ids[0]).toBe(ids[1])
  })

  it('피드백이 나오면 바꿔 볼 한 가지와 다음 행동을 보여 준다', async () => {
    const done = attempt({
      feedback: {
        id: 1,
        answerRevisionId: 1000,
        observation: '핵심 답이 앞에 있어요.',
        suggestion: '직접 한 일을 하나 덧붙여 보세요.',
        suggestionFocus: 'EVIDENCE',
        previousReview: null,
      },
      feedbackRequest: { id: 500, status: 'SUCCEEDED' },
    })
    fakeServer({ 'GET /api/practices/1': ok(practiceWith([done])) })
    renderPage()

    expect(await screen.findByText('직접 한 일을 하나 덧붙여 보세요.')).toBeTruthy()
    expect(screen.getByText(/다음에 바꿔 볼 한 가지 · 근거/)).toBeTruthy()
    expect(screen.getByRole('button', { name: '이어서 질문 받기' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '다시 답하기' })).toBeTruthy()
  })

  it('처리 중인 피드백은 폴링하다가 끝나면 상세를 다시 읽는다', async () => {
    const server = fakeServer({
      'GET /api/practices/1': [
        ok(practiceWith([attempt()])),
        ok(
          practiceWith([
            attempt({
              feedback: {
                id: 1,
                answerRevisionId: 1000,
                observation: '잘 들었어요.',
                suggestion: null,
                suggestionFocus: null,
                previousReview: null,
              },
              feedbackRequest: { id: 500, status: 'SUCCEEDED' },
            }),
          ]),
        ),
      ],
      'GET /api/ai-requests/500': [ok({ id: 500, status: 'RUNNING' }), ok({ id: 500, status: 'SUCCEEDED' })],
    })
    renderPage()

    expect(await screen.findByText('잘 들었어요.', {}, { timeout: 5_000 })).toBeTruthy()
    expect(server.calls.filter((call) => call.key === 'GET /api/ai-requests/500')).toHaveLength(2)
  })

  it('다른 곳에서 먼저 고친 답변이면 충돌을 알리고 최신 내용을 다시 읽는다', async () => {
    const done = attempt({ feedbackRequest: { id: 500, status: 'FAILED' } })
    const server = fakeServer({
      'GET /api/practices/1': ok(practiceWith([done])),
      'POST /api/answers/100/revisions': () => ({
        status: 409,
        body: { status: 409, code: 'REVISION_CONFLICT', detail: '충돌' },
      }),
    })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '답변 고치기' }))
    const textarea = screen.getByLabelText('내 답변')
    await userEvent.clear(textarea)
    await userEvent.type(textarea, '고친 답변')
    await userEvent.click(screen.getByRole('button', { name: '고친 답변으로 피드백 받기' }))

    expect(await screen.findByText(/다른 곳에서 먼저 수정된 답변이에요/)).toBeTruthy()
    await waitFor(() =>
      expect(server.calls.filter((call) => call.key === 'GET /api/practices/1').length).toBeGreaterThanOrEqual(2),
    )
    expect(server.calls.find((call) => call.key === 'POST /api/answers/100/revisions')!.body).toEqual({
      baseRevisionNo: 1,
      content: '고친 답변',
    })
  })
})

function voiceAttempt(overrides: Partial<AnswerAttempt> = {}): AnswerAttempt {
  return attempt({
    inputMode: 'VOICE',
    currentRevision: null,
    feedbackRequest: null,
    recording: { id: 900, durationMs: 62_000 },
    transcriptionRequest: { id: 700, status: 'PENDING' },
    ...overrides,
  })
}

describe('음성 답변', () => {
  it('녹음을 글로 옮기는 동안 기다림을 보여 주고, 끝나면 옮긴 문구와 고치기를 보여 준다', async () => {
    const transcribed = voiceAttempt({
      currentRevision: { id: 1000, revisionNo: 1, content: '레디스로 캐시를 붙였습니다.' },
      feedbackRequest: { id: 500, status: 'PENDING' },
      transcriptionRequest: { id: 700, status: 'SUCCEEDED' },
    })
    const server = fakeServer({
      'GET /api/practices/1': [ok(practiceWith([voiceAttempt()])), ok(practiceWith([transcribed]))],
      'GET /api/ai-requests/700': [ok({ id: 700, status: 'RUNNING' }), ok({ id: 700, status: 'SUCCEEDED' })],
      'GET /api/ai-requests/500': ok({ id: 500, status: 'PENDING' }),
    })
    renderPage()

    expect(await screen.findByText('녹음을 글로 옮기고 있어요.')).toBeTruthy()
    expect(screen.getByLabelText('내 녹음').getAttribute('src')).toBe('/api/answers/100/recording')

    expect(await screen.findByText('레디스로 캐시를 붙였습니다.', {}, { timeout: 5_000 })).toBeTruthy()
    expect(screen.getByRole('button', { name: '글로 옮긴 문구 고치기' })).toBeTruthy()
    expect(server.calls.filter((call) => call.key === 'GET /api/ai-requests/700')).toHaveLength(2)
  })

  it('글로 옮기지 못했으면 녹음을 지우지 않고 다시 옮기거나 글로 답하게 한다', async () => {
    const failed = voiceAttempt({ transcriptionRequest: { id: 700, status: 'FAILED' } })
    const server = fakeServer({
      'GET /api/practices/1': ok(practiceWith([failed])),
      'POST /api/recordings/900/transcriptions': () => ({ status: 201, body: { id: 701, status: 'PENDING' } }),
    })
    renderPage()

    expect(await screen.findByText(/녹음을 글로 옮기지 못했어요/)).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: '다시 글로 옮기기' }))
    await waitFor(() =>
      expect(server.calls.some((call) => call.key === 'POST /api/recordings/900/transcriptions')).toBe(true),
    )

    await userEvent.click(screen.getByRole('button', { name: '글로 답하기' }))
    expect(screen.getByLabelText('내 답변')).toBeTruthy()
  })

  it('녹음할 수 있는 브라우저에서는 말로 답하기가 먼저 나오고, 마이크를 거부하면 같은 질문에서 글로 답하게 바꾼다', async () => {
    vi.stubGlobal('MediaRecorder', class {})
    vi.stubGlobal('navigator', {
      ...navigator,
      mediaDevices: {
        getUserMedia: () => Promise.reject(new DOMException('denied', 'NotAllowedError')),
      },
    })
    fakeServer({ 'GET /api/practices/1': ok(practiceWith([])) })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '녹음 시작' }))

    expect(await screen.findByText(/마이크 사용이 허락되지 않아 글로 답하도록 바꿨어요/)).toBeTruthy()
    expect(screen.getByLabelText('내 답변')).toBeTruthy()
    expect(screen.getByRole('heading', { name: '최근 프로젝트에서 본인이 맡은 기능 하나를 설명해 주세요.' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '말로 답하기' })).toBeTruthy()
  })
})
