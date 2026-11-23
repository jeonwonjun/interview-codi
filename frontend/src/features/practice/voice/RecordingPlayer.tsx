import { formatClock } from './recording'
import { recordingUrl } from '../practiceApi'
import styles from './Voice.module.css'

/** 저장된 녹음 다시 듣기. 누르기 전에는 받지 않는다(preload none). */
export function RecordingPlayer({ answerId, durationMs }: { answerId: number; durationMs: number }) {
  return (
    <div className={styles.saved}>
      <audio className={styles.player} controls preload="none" src={recordingUrl(answerId)} aria-label="내 녹음" />
      <span className={styles.label}>{formatClock(durationMs)}</span>
    </div>
  )
}
