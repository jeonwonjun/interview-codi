/**
 * 모든 API 호출이 지나는 곳. 인증 방식이 바뀌어도 화면 코드를 고치지 않도록 여기에 모은다.
 *
 * - 세션 쿠키는 같은 주소(Vite 프록시)라 자동으로 실린다.
 * - 상태를 바꾸는 요청에는 브라우저가 Origin 헤더를 붙이므로 따로 CSRF 토큰을 보내지 않는다.
 * - 오류는 백엔드의 ProblemDetail을 읽어 ApiError로 던진다. 화면은 code로 분기한다.
 */
export type ErrorCode =
  | 'LOGIN_REQUIRED'
  | 'FORBIDDEN_ORIGIN'
  | 'PRACTICE_NOT_FOUND'
  | 'PRACTICE_COMPLETED'
  | 'NOT_CURRENT_QUESTION'
  | 'REVISION_CONFLICT'
  | 'NOT_LATEST_REVISION'
  | 'NO_MORE_QUESTIONS'
  | 'INVALID_ANSWER_CONTENT'
  | 'INVALID_RECORDING'
  | 'FILE_TOO_LARGE'
  | 'RECORDING_NOT_FOUND'
  | 'TRANSCRIPTION_IN_PROGRESS'
  | (string & {})

export class ApiError extends Error {
  readonly status: number
  readonly code: ErrorCode | undefined

  constructor(status: number, code: ErrorCode | undefined, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }

  get isLoginRequired(): boolean {
    return this.status === 401
  }
}

type ProblemDetail = {
  title?: string
  detail?: string
  code?: string
}

let onUnauthorized: (() => void) | undefined

/** 로그인이 풀렸을 때(401) 화면 전체가 로그인 화면으로 돌아가도록 App이 등록한다. */
export function setUnauthorizedHandler(handler: (() => void) | undefined) {
  onUnauthorized = handler
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  // FormData는 브라우저가 multipart 경계를 붙인 Content-Type을 직접 정한다.
  const isForm = body instanceof FormData
  const response = await fetch(path, {
    method,
    headers: body === undefined || isForm ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
  })

  if (!response.ok) {
    const error = await toApiError(response)
    if (error.isLoginRequired) {
      onUnauthorized?.()
    }
    throw error
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail = {}
  if (response.headers.get('Content-Type')?.includes('json')) {
    problem = (await response.json().catch(() => ({}))) as ProblemDetail
  }
  const message = problem.detail ?? problem.title ?? `요청을 처리하지 못했어요. (${response.status})`
  return new ApiError(response.status, problem.code, message)
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  delete: <T>(path: string) => request<T>('DELETE', path),
}
