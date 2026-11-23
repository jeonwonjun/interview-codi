import { api } from '../../shared/api/client'

export type AiRequestStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'DISCARDED'

export type AiRequest = {
  id: number
  status: AiRequestStatus
}

export type AiRequestDetail = AiRequest & {
  kind: 'FEEDBACK' | 'FOLLOW_UP_QUESTION'
  discardReason: string | null
  answerRevisionId: number
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

export type AnswerAttempt = {
  id: number
  questionId: number
  attemptNo: number
  currentRevision: { id: number; revisionNo: number; content: string }
  feedback: Feedback | null
  feedbackRequest: AiRequest | null
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

export const practiceApi = {
  create: () => api.post<Practice>('/api/practices', { startMode: 'EXPERIENCE' }),
  list: () => api.get<{ items: PracticeSummary[] }>('/api/practices').then((response) => response.items),
  get: (practiceId: number) => api.get<Practice>(`/api/practices/${practiceId}`),
  complete: (practiceId: number) => api.post<Practice>(`/api/practices/${practiceId}/complete`),
  remove: (practiceId: number) => api.delete<void>(`/api/practices/${practiceId}`),
  submitAnswer: (questionId: number, clientRequestId: string, content: string) =>
    api.post<AnswerAttempt>(`/api/questions/${questionId}/answers`, { clientRequestId, content }),
  revise: (answerId: number, baseRevisionNo: number, content: string) =>
    api.post<AnswerAttempt>(`/api/answers/${answerId}/revisions`, { baseRevisionNo, content }),
  retryFeedback: (revisionId: number) => api.post<AiRequest>(`/api/answer-revisions/${revisionId}/feedback-requests`),
  requestFollowUp: (practiceId: number, answerRevisionId: number) =>
    api.post<AiRequest>(`/api/practices/${practiceId}/follow-up-requests`, { answerRevisionId }),
  skip: (questionId: number) => api.post<Question>(`/api/questions/${questionId}/skip`),
  getAiRequest: (requestId: number) => api.get<AiRequestDetail>(`/api/ai-requests/${requestId}`),
}
