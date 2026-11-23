import { Keyboard, Mic, RotateCcw, Square } from 'lucide-react'
import { useEffect } from 'react'
import { Button } from '../../../shared/ui/Button'
import { Card } from '../../../shared/ui/Card'
import { formatClock, MAX_DURATION_MS } from './recording'
import { type RecordedAudio, useRecorder } from './useRecorder'
import styles from './Voice.module.css'

type Props = {
  submitLabel: string
  busy: boolean
  /** 녹음을 올린다. 실패하면 false를 돌려주고, 녹음은 지우지 않아 그대로 다시 보낼 수 있다. */
  onSubmit: (audio: RecordedAudio) => Promise<boolean>
  onUseText: () => void
  /** 마이크를 쓸 수 없을 때. 화면은 같은 질문에서 글로 답하도록 바꾼다. */
  onUnavailable: (reason: 'denied' | 'no-device' | 'unsupported') => void
  onRecordingStart?: () => void
  onCancel?: () => void
}

const LEVEL_BARS = 5

/**
 * 음성 답변. 녹음 → 들어 보기 → 제출. 2초 미만·무음이면 올리지 않고 다시 녹음하거나 글로 답하게 한다.
 * 약 1분은 안내일 뿐이고, 3분이 되면 멈추고 그때까지를 제출할지 묻는다.
 */
export function VoiceAnswer({ submitLabel, busy, onSubmit, onUseText, onUnavailable, onRecordingStart, onCancel }: Props) {
  const { state, start, stop, reset } = useRecorder()

  useEffect(() => {
    if (state.status === 'unavailable') {
      onUnavailable(state.reason)
    }
  }, [state, onUnavailable])

  const begin = () => {
    onRecordingStart?.()
    void start()
  }

  const submit = async (audio: RecordedAudio) => {
    if (await onSubmit(audio)) {
      reset()
    }
  }

  const textSwitch = (
    <Button variant="ghost" size="sm" icon={Keyboard} onClick={onUseText} disabled={busy || state.status === 'recording'}>
      글로 답하기
    </Button>
  )

  const cancelButton = onCancel && (
    <Button
      variant="ghost"
      size="sm"
      onClick={() => {
        reset()
        onCancel()
      }}
      disabled={busy}
    >
      취소
    </Button>
  )

  return (
    <Card as="div" aria-label="음성 답변">
      {state.status === 'recording' ? (
        <div className={styles.recorder}>
          <button type="button" className={`${styles.recordButton} ${styles.recording}`} onClick={stop}>
            <Square size={22} fill="currentColor" aria-hidden="true" />
            <span className={styles.visuallyHidden}>녹음 마치기</span>
          </button>
          <LevelMeter level={state.level} />
          <p className={styles.clock} aria-live="off">
            <span className={styles.recDot} aria-hidden="true" />
            {formatClock(state.elapsedMs)} <span className={styles.clockLimit}>/ {formatClock(MAX_DURATION_MS)}</span>
          </p>
          <p className={styles.hint}>다 말했으면 버튼을 눌러 녹음을 마쳐요.</p>
        </div>
      ) : state.status === 'recorded' ? (
        <Recorded
          audio={state.audio}
          submitLabel={submitLabel}
          busy={busy}
          onSubmit={() => void submit(state.audio)}
          onRetake={begin}
          textSwitch={textSwitch}
          cancel={cancelButton}
        />
      ) : (
        <div className={styles.recorder}>
          <button
            type="button"
            className={styles.recordButton}
            onClick={begin}
            disabled={busy || state.status === 'requesting'}
          >
            <Mic size={26} aria-hidden="true" />
            <span className={styles.visuallyHidden}>녹음 시작</span>
          </button>
          <p className={styles.hint}>
            {state.status === 'requesting'
              ? '마이크 사용을 허락해 주세요.'
              : state.status === 'error'
                ? '녹음을 시작하지 못했어요. 다시 누르거나 글로 답해 주세요.'
                : '버튼을 누르고 1분 정도 말해 보세요. 3분까지 녹음돼요.'}
          </p>
          <div className={styles.actions}>
            {cancelButton}
            {textSwitch}
          </div>
        </div>
      )}
    </Card>
  )
}

function Recorded({
  audio,
  submitLabel,
  busy,
  onSubmit,
  onRetake,
  textSwitch,
  cancel,
}: {
  audio: RecordedAudio
  submitLabel: string
  busy: boolean
  onSubmit: () => void
  onRetake: () => void
  textSwitch: React.ReactNode
  cancel: React.ReactNode
}) {
  if (audio.verdict !== 'ok') {
    return (
      <div className={styles.recorder} role="alert">
        <p className={styles.problem}>
          {audio.verdict === 'too-short'
            ? '녹음이 너무 짧아요. 다시 녹음하거나 글로 답해 주세요.'
            : '소리가 들리지 않았어요. 마이크를 확인하고 다시 녹음하거나 글로 답해 주세요.'}
        </p>
        <div className={styles.actions}>
          {cancel}
          {textSwitch}
          <Button variant="secondary" icon={RotateCcw} onClick={onRetake}>
            다시 녹음
          </Button>
        </div>
      </div>
    )
  }
  return (
    <div className={styles.review}>
      <span className={styles.label}>
        내 녹음 · {formatClock(audio.durationMs)}
        {audio.autoStopped && ' · 3분이 되어 녹음을 멈췄어요. 여기까지 제출할까요?'}
      </span>
      <audio className={styles.player} controls src={audio.url} />
      <div className={styles.reviewActions}>
        <div className={styles.minorActions}>
          {cancel}
          {textSwitch}
        </div>
        <div className={styles.mainActions}>
          <Button variant="secondary" icon={RotateCcw} onClick={onRetake} disabled={busy}>
            다시 녹음
          </Button>
          <Button onClick={onSubmit} loading={busy}>
            {submitLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}

/** 지금 소리 크기. 말소리(RMS 약 0.05~0.3)가 막대 대부분을 채우도록 늘려 보여 준다. */
function LevelMeter({ level }: { level: number }) {
  const filled = Math.min(LEVEL_BARS, Math.round(Math.sqrt(level / 0.3) * LEVEL_BARS))
  return (
    <div className={styles.meter} aria-hidden="true">
      {Array.from({ length: LEVEL_BARS }, (_, index) => (
        <span key={index} className={index < filled ? `${styles.bar} ${styles.barOn}` : styles.bar} />
      ))}
    </div>
  )
}
