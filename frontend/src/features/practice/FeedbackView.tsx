import { Button } from '../../shared/ui/Button'
import { FOCUS_LABEL } from './labels'
import { type AnswerAttempt, isActive } from './practiceApi'
import styles from './Practice.module.css'

type Props = {
  attempt: AnswerAttempt
  slow?: boolean
  onRetry?: () => void
  busy?: boolean
}

/**
 * 답변 시도의 현재 버전에 대한 피드백 영역. 교정 전 버전의 피드백은 서버가 넣지 않으므로 여기서도 보이지 않는다.
 * 피드백에는 빨간색·점수를 쓰지 않고, 바꿔 볼 한 가지만 노란 카드로 보여 준다(DESIGN.md).
 */
export function FeedbackView({ attempt, slow = false, onRetry, busy = false }: Props) {
  const { feedback, feedbackRequest } = attempt
  if (feedback) {
    return (
      <div className={styles.card} aria-label="피드백">
        <span className={styles.label}>피드백</span>
        <p className={styles.observation}>{feedback.observation}</p>
        {feedback.previousReview && (
          <p className={styles.observation}>
            <strong>지난번 개선점: </strong>
            {feedback.previousReview}
          </p>
        )}
        {feedback.suggestion ? (
          <div className={styles.tip}>
            <span className={styles.tipLabel}>
              다음에 바꿔 볼 한 가지
              {feedback.suggestionFocus && ` · ${FOCUS_LABEL[feedback.suggestionFocus]}`}
            </span>
            <span>{feedback.suggestion}</span>
          </div>
        ) : (
          <p className={styles.caption}>이번 답변은 지금처럼 말해도 충분해요. 다음 질문으로 넘어가 보세요.</p>
        )}
      </div>
    )
  }
  if (isActive(feedbackRequest)) {
    return (
      <div className={styles.card} role="status">
        <div className={styles.waiting}>
          <span className={styles.spinner} aria-hidden="true" />
          <span>{slow ? '피드백이 오래 걸리고 있어요. 조금만 더 기다려 주세요.' : '피드백을 만들고 있어요.'}</span>
        </div>
      </div>
    )
  }
  return (
    <div className={styles.card} role="status">
      <p className={styles.observation}>피드백을 만들지 못했어요. 답변은 그대로 저장되어 있어요.</p>
      {onRetry && (
        <div>
          <Button variant="secondary" onClick={onRetry} disabled={busy}>
            피드백 다시 요청
          </Button>
        </div>
      )}
    </div>
  )
}
