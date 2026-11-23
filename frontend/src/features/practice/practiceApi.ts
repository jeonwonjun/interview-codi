import { api } from '../../shared/api/client'

export type AiRequestStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'DISCARDED'

export type AiRequest = {
  id: number
  status: AiRequestStatus
}

export type AiRequestDetail = AiRequest & {
  kind: 'FEEDBACK' | 'FOLLOW_UP_QUESTION' | 'TRANSCRIBE'
  discardReason: string | null
  /** TRANSCRIBE는 null(버전은 인식이 끝나야 생긴다). */
  answerRevisionId: number | null
  /** TRANSCRIBE의 입력 녹음. 다른 종류는 null. */
  recordingId: number | null
  feedbackId: number | null
  questionId: number | null
}

export type Feedback = {
  id: number
  answerRevisionId: number
  observation: string
  suggestion: string | null
  suggestionFocus: 'ACCURACY' | 'ANSWER' | 'EVIDENCE' | 'ORDER' | null
  previousReview: string | null
}

export type AnswerRevision = { id: number; revisionNo: number; content: string }

export type AnswerAttempt = {
  id: number
  questionId: number
  attemptNo: number
  inputMode: 'TEXT' | 'VOICE'
  /** 인식이 끝나지 않은(또는 실패한) 음성 시도는 null. */
  currentRevision: AnswerRevision | null
  feedback: Feedback | null
  feedbackRequest: AiRequest | null
  /** 음성 시도의 녹음. 파일은 recordingUrl(시도 ID)로 받는다. */
  recording: { id: number; durationMs: number } | null
  /** 녹음의 가장 최근 인식 요청. 텍스트 시도는 null. */
  transcriptionRequest: AiRequest | null
}

/** 글로 옮겨진 시도(텍스트 시도는 항상). 버전이 있어야 고치기·피드백·꼬리질문을 할 수 있다. */
export type TranscribedAttempt = AnswerAttempt & { currentRevision: AnswerRevision }

export function hasRevision(attempt: AnswerAttempt | undefined): attempt is TranscribedAttempt {
  return attempt?.currentRevision != null
}

export type Question = {
  id: number
  orderNo: number
  content: string
  sourceKind: 'EXPERIENCE_PRESET' | 'FOLLOW_UP'
  skipped: boolean
  current: boolean
  attempts: AnswerAttempt[]
}

export type Practice = {
  id: number
  startMode: 'EXPERIENCE' | 'RESUME'
  status: 'IN_PROGRESS' | 'COMPLETED'
  createdAt: string
  completedAt: string | null
  canProceed: boolean
  readOnlyReason: string | null
  questions: Question[]
  followUpRequest: AiRequest | null
}

export type PracticeSummary = {
  id: number
  startMode: 'EXPERIENCE' | 'RESUME'
  status: 'IN_PROGRESS' | 'COMPLETED'
  createdAt: string
  completedAt: string | null
  questionCount: number
  /** 답변 시도 수(다시 답하기 포함). 0이면 시작만 한 연습이라 달력에 표시하지 않는다. */
  answerCount: number
  /** 마지막 답변 시각. 달력은 이 날짜로 묶는다(며칠에 걸쳐 이어 한 연습 때문에). 답이 없으면 null. */
  lastAnsweredAt: string | null
  canProceed: boolean
}

export const ACTIVE_STATUSES: AiRequestStatus[] = ['PENDING', 'RUNNING']

export function isActive(request: AiRequest | null | undefined): request is AiRequest {
  return request != null && ACTIVE_STATUSES.includes(request.status)
}

/** 녹음 재생 주소. 서버가 매번 소유자·삭제 상태를 확인한 뒤 흘려보낸다. */
export function recordingUrl(answerId: number): string {
  return `/api/answers/${answerId}/recording`
}

function extensionOf(mimeType: string): string {
  if (mimeType.includes('mp4')) {
    return 'm4a'
  }
  if (mimeType.includes('ogg')) {
    return 'ogg'
  }
  return 'webm'
}

export const practiceApi = {
  create: () => api.post<Practice>('/api/practices', { startMode: 'EXPERIENCE' }),
  list: () => api.get<{ items: PracticeSummary[] }>('/api/practices').then((response) => response.items),
  get: (practiceId: number) => api.get<Practice>(`/api/practices/${practiceId}`),
  complete: (practiceId: number) => api.post<Practice>(`/api/practices/${practiceId}/complete`),
  remove: (practiceId: number) => api.delete<void>(`/api/practices/${practiceId}`),
  submitAnswer: (questionId: number, clientRequestId: string, content: string) =>
    api.post<AnswerAttempt>(`/api/questions/${questionId}/answers`, { clientRequestId, content }),
  submitVoice: (questionId: number, clientRequestId: string, audio: Blob, durationMs: number) => {
    const form = new FormData()
    form.append('audio', audio, `answer.${extensionOf(audio.type)}`)
    form.append('clientRequestId', clientRequestId)
    form.append('durationMs', String(Math.round(durationMs)))
    return api.post<AnswerAttempt>(`/api/questions/${questionId}/voice-answers`, form)
  },
  retryTranscription: (recordingId: number) => api.post<AiRequest>(`/api/recordings/${recordingId}/transcriptions`),
  revise: (answerId: number, baseRevisionNo: number, content: string) =>
    api.post<AnswerAttempt>(`/api/answers/${answerId}/revisions`, { baseRevisionNo, content }),
  retryFeedback: (revisionId: number) => api.post<AiRequest>(`/api/answer-revisions/${revisionId}/feedback-requests`),
  requestFollowUp: (practiceId: number, answerRevisionId: number) =>
    api.post<AiRequest>(`/api/practices/${practiceId}/follow-up-requests`, { answerRevisionId }),
  skip: (questionId: number) => api.post<Question>(`/api/questions/${questionId}/skip`),
  getAiRequest: (requestId: number) => api.get<AiRequestDetail>(`/api/ai-requests/${requestId}`),
}
