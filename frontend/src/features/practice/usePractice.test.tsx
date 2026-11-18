import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { usePractice } from './usePractice'

afterEach(() => vi.unstubAllGlobals())

describe('usePractice', () => {
  it('먼저 보낸 상세 요청의 응답이 늦게 와도 나중 요청의 결과를 덮어쓰지 않는다', async () => {
    const resolvers: ((response: Response) => void)[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise<Response>((resolve) => resolvers.push(resolve))),
    )
    const response = (status: string) =>
      new Response(JSON.stringify({ id: 1, status, questions: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })

    const { result } = renderHook(() => usePractice(1))
    await act(async () => {
      await Promise.resolve()
    })
    let second: Promise<void>
    act(() => {
      second = result.current.reload()
    })

    await act(async () => {
      resolvers[1](response('COMPLETED'))
      await second
    })
    await act(async () => {
      resolvers[0](response('IN_PROGRESS'))
      await new Promise((resolve) => setTimeout(resolve, 0))
    })

    expect(result.current.state).toMatchObject({ status: 'loaded', practice: { status: 'COMPLETED' } })
  })
})
