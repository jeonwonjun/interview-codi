/** 녹음 규칙. 화면과 테스트가 같은 값을 쓰도록 한곳에 둔다(설계: docs/voice-practice-design.md 1절·5-4). */

/** 1분 안내의 3배. 넘으면 녹음을 멈추고 그때까지를 제출할지 묻는다. */
export const MAX_DURATION_MS = 180_000
/** 이보다 짧으면 올리지 않는다. 실수로 누른 녹음을 답변으로 평가하지 않기 위해서다. */
export const MIN_DURATION_MS = 2_000
/**
 * 녹음 내내 소리 크기(RMS, 0~1)의 최댓값이 이보다 작으면 무음으로 본다.
 * 조용한 방의 배경 소음은 약 0.005~0.01, 보통 말소리는 0.05 이상이다(노트북 내장 마이크 기준 가정).
 */
export const SILENCE_LEVEL = 0.02

/** 브라우저가 만들 수 있는 형식 중 서버가 받는 것. Chrome·Firefox는 webm, Safari는 mp4를 고른다. */
const MIME_CANDIDATES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus']

export function pickMimeType(isTypeSupported: (type: string) => boolean): string | undefined {
  return MIME_CANDIDATES.find((type) => isTypeSupported(type))
}

export type RecordingVerdict = 'ok' | 'too-short' | 'silent'

export function judgeRecording(durationMs: number, peakLevel: number): RecordingVerdict {
  if (durationMs < MIN_DURATION_MS) {
    return 'too-short'
  }
  if (peakLevel < SILENCE_LEVEL) {
    return 'silent'
  }
  return 'ok'
}

/** 시간 소리 크기 표본의 RMS(0~1). */
export function rmsOf(samples: Float32Array): number {
  if (samples.length === 0) {
    return 0
  }
  let sum = 0
  for (const sample of samples) {
    sum += sample * sample
  }
  return Math.sqrt(sum / samples.length)
}

/** 0:42처럼 분:초. */
export function formatClock(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

export function isRecordingSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.MediaRecorder !== 'undefined' &&
    typeof navigator.mediaDevices?.getUserMedia === 'function'
  )
}
