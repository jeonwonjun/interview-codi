import { describe, expect, it } from 'vitest'
import { formatClock, judgeRecording, pickMimeType, rmsOf } from './recording'

describe('녹음 규칙', () => {
  it('2초보다 짧은 녹음은 올리지 않는다', () => {
    expect(judgeRecording(1_999, 0.3)).toBe('too-short')
  })

  it('소리 크기가 내내 기준보다 작으면 무음으로 본다', () => {
    expect(judgeRecording(30_000, 0.01)).toBe('silent')
    expect(judgeRecording(30_000, 0.05)).toBe('ok')
  })

  it('서버가 받는 형식 중 브라우저가 만들 수 있는 첫 형식을 고른다', () => {
    expect(pickMimeType((type) => type === 'audio/mp4')).toBe('audio/mp4')
    expect(pickMimeType((type) => type.startsWith('audio/webm'))).toBe('audio/webm;codecs=opus')
    expect(pickMimeType(() => false)).toBeUndefined()
  })

  it('RMS와 분:초 표시', () => {
    expect(rmsOf(new Float32Array([0.5, -0.5, 0.5, -0.5]))).toBeCloseTo(0.5)
    expect(rmsOf(new Float32Array())).toBe(0)
    expect(formatClock(42_900)).toBe('0:42')
    expect(formatClock(180_000)).toBe('3:00')
  })
})
