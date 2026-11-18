import { useEffect, useRef, useState } from 'react'
import { type AiRequestDetail, isActive, practiceApi } from './practiceApi'

const INTERVAL_MS = 1_500
const SLOW_INTERVAL_MS = 5_000
const SLOW_AFTER_MS = 120_000

/**
 * 처리 중인 AI 요청의 상태를 폴링한다. 끝난 상태가 되면 {@code onFinished}를 한 번 부른다.
 * 2분이 지나면 {@code slow}를 켜고 간격을 5초로 늦춘다. 요청 ID가 바뀌면 이전 폴링은 멈춘다.
 */
export function useAiRequestPolling(requestId: number | null, onFinished: (detail: AiRequestDetail) => void) {
  const [slow, setSlow] = useState(false)
  const onFinishedRef = useRef(onFinished)

  useEffect(() => {
    onFinishedRef.current = onFinished
  }, [onFinished])

  useEffect(() => {
    if (requestId == null) {
      return
    }
    let stopped = false
    let timer: ReturnType<typeof setTimeout> | undefined
    const startedAt = Date.now()

    const poll = async () => {
      try {
        const detail = await practiceApi.getAiRequest(requestId)
        if (stopped) {
          return
        }
        if (!isActive(detail)) {
          onFinishedRef.current(detail)
          return
        }
      } catch {
        // 일시적인 네트워크 오류는 다음 주기에 다시 시도한다. 401은 클라이언트가 로그인 화면으로 보낸다.
        if (stopped) {
          return
        }
      }
      const elapsed = Date.now() - startedAt
      setSlow(elapsed >= SLOW_AFTER_MS)
      timer = setTimeout(poll, elapsed >= SLOW_AFTER_MS ? SLOW_INTERVAL_MS : INTERVAL_MS)
    }

    timer = setTimeout(poll, INTERVAL_MS)
    return () => {
      stopped = true
      clearTimeout(timer)
      setSlow(false)
    }
  }, [requestId])

  return { slow }
}
