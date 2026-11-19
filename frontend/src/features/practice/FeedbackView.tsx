import { Lightbulb, RotateCcw } from 'lucide-react'
import { Button } from '../../shared/ui/Button'
import { Card } from '../../shared/ui/Card'
import { Pending } from '../../shared/ui/Pending'
import { Reveal, RevealText } from '../../shared/ui/Reveal'
import { revealTextDuration } from '../../shared/ui/revealText'
import { FOCUS_LABEL } from './labels'
import { type AnswerAttempt, isActive } from './practiceApi'
import styles from './Practice.module.css'

const STAGGER_MS = 80

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
 * 도착할 때는 카드가 먼저 나타나고, 관찰·지난번 개선점은 말하듯 몇 어절씩 이어지고, 바꿔 볼 한 가지는 마지막에 노란색이 스며든다.
 */
export function FeedbackView({ attempt, slow = false, onRetry, busy = false, animate = false }: Props) {
  const { feedback, feedbackRequest } = attempt
  if (feedback) {
    // 카드(0ms) → 관찰을 몇 어절씩 → 지난번 개선점 → 바꿔 볼 한 가지. 앞 내용이 다 나타난 뒤 다음이 시작한다.
    const observationAt = 200
    const previousAt = observationAt + revealTextDuration(feedback.observation) + STAGGER_MS
    const tipAt =
      (feedback.previousReview ? previousAt + revealTextDuration(feedback.previousReview) : previousAt) + STAGGER_MS
    return (
      <Card reveal={animate} aria-label="피드백">
        <span className={styles.label}>피드백</span>
        <p className={styles.observation}>
          {animate ? <RevealText text={feedback.observation} delay={observationAt} /> : feedback.observation}
        </p>
        {feedback.previousReview && (
          <p className={styles.observation}>
            <strong>지난번 개선점: </strong>
            {animate ? <RevealText text={feedback.previousReview} delay={previousAt} /> : feedback.previousReview}
          </p>
        )}
        {feedback.suggestion ? (
          <Step animate={animate} delay={tipAt}>
            <div
              className={[styles.tip, animate && styles.tipArrive].filter(Boolean).join(' ')}
              style={animate ? { animationDelay: `${tipAt}ms` } : undefined}
            >
              <span className={styles.tipLabel}>
                <Lightbulb size={14} strokeWidth={2.25} aria-hidden="true" />
                다음에 바꿔 볼 한 가지
                {feedback.suggestionFocus && ` · ${FOCUS_LABEL[feedback.suggestionFocus]}`}
              </span>
              <span>{feedback.suggestion}</span>
            </div>
          </Step>
        ) : (
          <Step animate={animate} delay={tipAt}>
            <p className={styles.caption}>이번 답변은 지금처럼 말해도 충분해요. 다음 질문으로 넘어가 보세요.</p>
          </Step>
        )}
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
          <Button variant="secondary" icon={RotateCcw} onClick={onRetry} loading={busy}>
            피드백 다시 요청
          </Button>
        </div>
      )}
    </Card>
  )
}

function Step({ animate, delay, children }: { animate: boolean; delay: number; children: React.ReactNode }) {
  return animate ? <Reveal delay={delay}>{children}</Reveal> : <>{children}</>
}
