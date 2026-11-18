import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../../shared/api/client'
import { type Practice, practiceApi } from './practiceApi'

type State =
  | { status: 'loading' }
  | { status: 'loaded'; practice: Practice }
  | { status: 'not-found' }
  | { status: 'error'; message: string }

/**
 * 연습 상세를 읽는다. 화면 상태의 기준은 서버 상세이고, 변경 요청 뒤에는 {@code reload}로 다시 읽는다.
 * 여러 번 다시 읽을 때 늦게 도착한 이전 응답이 최신 응답을 덮어쓰지 않도록 요청 순번을 확인한다.
 */
export function usePractice(practiceId: number) {
  const [state, setState] = useState<State>({ status: 'loading' })
  const sequence = useRef(0)

  const reload = useCallback(async () => {
    const mine = ++sequence.current
    try {
      const practice = await practiceApi.get(practiceId)
      if (mine === sequence.current) {
        setState({ status: 'loaded', practice })
      }
    } catch (error) {
      if (mine !== sequence.current) {
        return
      }
      if (error instanceof ApiError && error.status === 404) {
        setState({ status: 'not-found' })
      } else if (!(error instanceof ApiError && error.isLoginRequired)) {
        setState({ status: 'error', message: '연습을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.' })
      }
    }
  }, [practiceId])

  useEffect(() => {
    void Promise.resolve().then(reload)
  }, [reload])

  return { state, reload }
}
