import styles from './PulseDot.module.css'

/** 기다리는 중임을 보여 주는 숨쉬는 점. 스스로 상태를 알리지 않으므로 옆에 문구를 함께 둔다. */
export function PulseDot() {
  return <span className={styles.dot} aria-hidden="true" />
}
