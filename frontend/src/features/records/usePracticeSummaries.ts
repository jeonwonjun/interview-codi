import { useCallback, useEffect, useState } from 'react'
import { practiceApi, type PracticeSummary } from '../practice/practiceApi'

type State = { status: 'loading' } | { status: 'loaded'; items: PracticeSummary[] } | { status: 'error' }

/** 기록 목록. 달력·오른쪽 칸·첫 화면이 같이 쓴다. 실패해도 연습은 할 수 있으므로 오류는 상태로만 알린다. */
export function usePracticeSummaries() {
  const [state, setState] = useState<State>({ status: 'loading' })

  const reload = useCallback(async () => {
    try {
      setState({ status: 'loaded', items: await practiceApi.list() })
    } catch {
      setState({ status: 'error' })
    }
  }, [])

  useEffect(() => {
    void Promise.resolve().then(reload)
  }, [reload])

  return { state, reload }
}
