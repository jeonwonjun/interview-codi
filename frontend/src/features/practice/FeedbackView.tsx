import { Button } from '../../shared/ui/Button'
import { Card } from '../../shared/ui/Card'
import { Pending } from '../../shared/ui/Pending'
import { Reveal } from '../../shared/ui/Reveal'
import { FOCUS_LABEL } from './labels'
import { type AnswerAttempt, isActive } from './practiceApi'
import styles from './Practice.module.css'

type Props = {
  attempt: AnswerAttempt
  slow?: boolean
  onRetry?: () => void
  busy?: boolean
  /** 피드백이 방금 도착했을 때처럼 순서대로 나타나게 할지. 기록 화면에서는 끈다. */
  animate?: boolean
}

/**
 * 답변 시도의 현재 버전에 대한 피드백 영역. 교정 전 버전의 피드백은 서버가 넣지 않으므로 여기서도 보이지 않는다.
 * 피드백에는 빨간색·점수를 쓰지 않고, 바꿔 볼 한 가지만 노란 카드로 보여 준다(DESIGN.md).
 * 도착할 때는 카드가 먼저, 관찰 → 지난번 개선점 → 바꿔 볼 한 가지 순서로 나타난다.
 */
export function FeedbackView({ attempt, slow = false, onRetry, busy = false, animate = false }: Props) {
  const { feedback, feedbackRequest } = attempt
  if (feedback) {
    const Step = animate ? Reveal : Plain
    let index = 0
    return (
      <Card reveal={animate} aria-label="피드백">
        <span className={styles.label}>피드백</span>
        <Step index={index++}>
          <p className={styles.observation}>{feedback.observation}</p>
        </Step>
        {feedback.previousReview && (
          <Step index={index++}>
            <p className={styles.observation}>
              <strong>지난번 개선점: </strong>
              {feedback.previousReview}
            </p>
          </Step>
        )}
        <Step index={index}>
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
        </Step>
      </Card>
    )
  }
  if (isActive(feedbackRequest)) {
    return (
      <Card>
        <Pending>{slow ? '피드백이 오래 걸리고 있어요. 조금만 더 기다려 주세요.' : '피드백을 만들고 있어요.'}</Pending>
      </Card>
    )
  }
  return (
    <Card role="status">
      <p className={styles.observation}>피드백을 만들지 못했어요. 답변은 그대로 저장되어 있어요.</p>
      {onRetry && (
        <div>
          <Button variant="secondary" onClick={onRetry} loading={busy}>
            피드백 다시 요청
          </Button>
        </div>
      )}
    </Card>
  )
}

function Plain({ children }: { index?: number; children: React.ReactNode }) {
  return <>{children}</>
}
