import { useCallback, useEffect, useRef, useState } from 'react'
import { isRecordingSupported, judgeRecording, MAX_DURATION_MS, pickMimeType, type RecordingVerdict, rmsOf } from './recording'

export type RecordedAudio = {
  blob: Blob
  url: string
  durationMs: number
  verdict: RecordingVerdict
  /** 3분에 닿아 자동으로 멈췄는지. */
  autoStopped: boolean
}

export type RecorderState =
  | { status: 'idle' }
  | { status: 'requesting' }
  | { status: 'recording'; elapsedMs: number; level: number }
  | { status: 'recorded'; audio: RecordedAudio }
  /** 권한 거부·장치 없음·브라우저 미지원. 같은 질문에서 글로 답하게 한다. */
  | { status: 'unavailable'; reason: 'denied' | 'no-device' | 'unsupported' }
  | { status: 'error' }

// 소리 크기 표시를 이 간격으로만 다시 그린다. 매 프레임(60fps) 다시 그리지 않는다.
const LEVEL_UPDATE_MS = 100

type Session = {
  stream: MediaStream
  recorder: MediaRecorder
  context: AudioContext
  chunks: Blob[]
  startedAt: number
  peak: number
  frame: number
  autoStopped: boolean
}

/**
 * 마이크 녹음. MediaRecorder로 파일을 만들고, 같은 소리를 AnalyserNode로 재서 소리 크기 표시와 무음 판정에 쓴다.
 * 녹음은 브라우저 메모리에만 있다. 올리기에 실패해도 다시 보낼 수 있게, 제출이 끝나기 전에는 지우지 않는다.
 */
export function useRecorder() {
  const [state, setState] = useState<RecorderState>(() =>
    isRecordingSupported() ? { status: 'idle' } : { status: 'unavailable', reason: 'unsupported' },
  )
  const session = useRef<Session | null>(null)
  const recordedUrl = useRef<string | null>(null)

  const release = useCallback(() => {
    const current = session.current
    if (!current) {
      return
    }
    cancelAnimationFrame(current.frame)
    current.stream.getTracks().forEach((track) => track.stop())
    void current.context.close().catch(() => undefined)
    session.current = null
  }, [])

  const revoke = useCallback(() => {
    if (recordedUrl.current) {
      URL.revokeObjectURL(recordedUrl.current)
      recordedUrl.current = null
    }
  }, [])

  useEffect(
    () => () => {
      if (session.current?.recorder.state === 'recording') {
        session.current.recorder.onstop = null
        session.current.recorder.stop()
      }
      release()
      revoke()
    },
    [release, revoke],
  )

  const stop = useCallback(() => {
    const current = session.current
    if (current && current.recorder.state === 'recording') {
      current.recorder.stop()
    }
  }, [])

  const start = useCallback(async () => {
    if (!isRecordingSupported()) {
      setState({ status: 'unavailable', reason: 'unsupported' })
      return
    }
    revoke()
    setState({ status: 'requesting' })
    let stream: MediaStream
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true },
      })
    } catch (error) {
      const name = error instanceof DOMException ? error.name : ''
      if (name === 'NotAllowedError' || name === 'SecurityError') {
        setState({ status: 'unavailable', reason: 'denied' })
      } else if (name === 'NotFoundError' || name === 'OverconstrainedError') {
        setState({ status: 'unavailable', reason: 'no-device' })
      } else {
        setState({ status: 'error' })
      }
      return
    }

    const mimeType = pickMimeType((type) => MediaRecorder.isTypeSupported(type))
    const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined)
    const context = new AudioContext()
    const analyser = context.createAnalyser()
    analyser.fftSize = 2048
    context.createMediaStreamSource(stream).connect(analyser)
    const samples = new Float32Array(analyser.fftSize)

    const current: Session = {
      stream,
      recorder,
      context,
      chunks: [],
      startedAt: performance.now(),
      peak: 0,
      frame: 0,
      autoStopped: false,
    }
    session.current = current

    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        current.chunks.push(event.data)
      }
    }
    recorder.onstop = () => {
      const durationMs = performance.now() - current.startedAt
      const blob = new Blob(current.chunks, { type: recorder.mimeType || mimeType || 'audio/webm' })
      release()
      const url = URL.createObjectURL(blob)
      recordedUrl.current = url
      setState({
        status: 'recorded',
        audio: {
          blob,
          url,
          durationMs,
          verdict: judgeRecording(durationMs, current.peak),
          autoStopped: current.autoStopped,
        },
      })
    }

    let lastDrawn = 0
    const measure = (now: number) => {
      analyser.getFloatTimeDomainData(samples)
      const level = rmsOf(samples)
      current.peak = Math.max(current.peak, level)
      const elapsedMs = now - current.startedAt
      if (elapsedMs >= MAX_DURATION_MS) {
        current.autoStopped = true
        recorder.stop()
        return
      }
      if (now - lastDrawn >= LEVEL_UPDATE_MS) {
        lastDrawn = now
        setState({ status: 'recording', elapsedMs, level })
      }
      current.frame = requestAnimationFrame(measure)
    }

    // 1초마다 조각을 받아 두면, 멈추는 순간 브라우저가 늦게 끝내도 앞부분을 잃지 않는다.
    recorder.start(1000)
    setState({ status: 'recording', elapsedMs: 0, level: 0 })
    current.frame = requestAnimationFrame(measure)
  }, [release, revoke])

  /** 녹음을 버리고 처음 상태로 돌아간다. 제출에 성공한 뒤에도 부른다. */
  const reset = useCallback(() => {
    revoke()
    setState(isRecordingSupported() ? { status: 'idle' } : { status: 'unavailable', reason: 'unsupported' })
  }, [revoke])

  return { state, start, stop, reset }
}
