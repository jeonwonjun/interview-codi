import { vi } from 'vitest'

type Handler = (body: unknown) => { status: number; body?: unknown } | Promise<{ status: number; body?: unknown }>

/**
 * 테스트용 가짜 API 서버. {@code "POST /api/..."} 형태의 키로 응답을 정하고, 받은 요청을 기록한다.
 * 같은 키에 응답을 여러 개 넣으면 차례대로 돌려준다(마지막 응답은 반복).
 */
export function fakeServer(routes: Record<string, Handler | Handler[]>) {
  const calls: { key: string; body: unknown }[] = []
  const queues = new Map(Object.entries(routes).map(([key, value]) => [key, Array.isArray(value) ? [...value] : [value]]))

  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${String(input)}`
    const body = init?.body ? JSON.parse(String(init.body)) : undefined
    calls.push({ key, body })
    const queue = queues.get(key)
    if (!queue) {
      return new Response(JSON.stringify({ title: 'Not Found' }), { status: 404 })
    }
    const handler = queue.length > 1 ? queue.shift()! : queue[0]
    const result = await handler(body)
    if (result.status === 204) {
      return new Response(null, { status: 204 })
    }
    const isError = result.status >= 400
    return new Response(JSON.stringify(result.body ?? {}), {
      status: result.status,
      headers: { 'Content-Type': isError ? 'application/problem+json' : 'application/json' },
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  return { calls, fetchMock }
}

export const ok = (body: unknown) => () => ({ status: 200, body })
