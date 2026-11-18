import { useCallback, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../shared/api/client'
import { Badge } from '../../shared/ui/Badge'
import { Button } from '../../shared/ui/Button'
import { Card } from '../../shared/ui/Card'
import { ErrorBanner } from '../../shared/ui/ErrorBanner'
import { Pending } from '../../shared/ui/Pending'
import { AnswerForm } from './AnswerForm'
import { FeedbackView } from './FeedbackView'
import { formatDateTime } from './labels'
import { type AnswerAttempt, isActive, type Practice, practiceApi, type Question } from './practiceApi'
import styles from './Practice.module.css'
import { useAiRequestPolling } from './useAiRequestPolling'
import { usePractice } from './usePractice'

export function PracticePage() {
  const { practiceId } = useParams()
  const { state, reload } = usePractice(Number(practiceId))

  if (state.status === 'loading') {
    return null
  }
  if (state.status === 'not-found') {
    return (
      <div className={styles.page}>
        <ErrorBanner>연습을 찾을 수 없어요. 삭제됐거나 다른 계정의 연습일 수 있어요.</ErrorBanner>
        <Link to="/">처음으로</Link>
      </div>
    )
  }
  if (state.status === 'error') {
    return (
      <div className={styles.page}>
        <ErrorBanner>{state.message}</ErrorBanner>
      </div>
    )
  }
  return state.practice.canProceed ? (
    <InProgressView practice={state.practice} reload={reload} />
  ) : (
    <RecordView practice={state.practice} />
  )
}

type Mode = 'view' | 'reanswer' | 'edit'

/** 오류 코드별 안내. 그 밖의 오류는 서버가 준 문구를 쓴다. */
function messageOf(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return '서버에 연결하지 못했어요. 입력한 내용은 그대로 있어요. 잠시 후 다시 시도해 주세요.'
  }
  switch (error.code) {
    case 'REVISION_CONFLICT':
      return '다른 곳에서 먼저 수정된 답변이에요. 최신 내용으로 다시 불러왔어요.'
    case 'NO_MORE_QUESTIONS':
      return '준비한 질문을 모두 연습했어요. 연습을 마치거나 지금 질문에 다시 답해 보세요.'
    default:
      return error.message
  }
}

function InProgressView({ practice, reload }: { practice: Practice; reload: () => Promise<void> }) {
  const navigate = useNavigate()
  const [mode, setMode] = useState<Mode>('view')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // 같은 제출을 다시 보낼 때(네트워크 오류 등) 같은 값을 써서 서버가 한 번만 저장하게 한다.
  const clientRequestId = useRef(crypto.randomUUID())

  const current = practice.questions.find((question) => question.current) ?? practice.questions.at(-1)!
  const previous = practice.questions.filter((question) => question.id !== current.id)
  const latest = current.attempts.at(-1)
  const followUpPending = isActive(practice.followUpRequest)
  // 이 화면에서 기다리던 피드백만 도착할 때 순서대로 보여 준다. 새로 열었을 때 이미 있던 피드백은 바로 보여 준다.
  const [awaitedRevisionId, setAwaitedRevisionId] = useState<number | null>(null)
  const pendingRevisionId = latest && isActive(latest.feedbackRequest) ? latest.currentRevision.id : null
  if (pendingRevisionId != null && pendingRevisionId !== awaitedRevisionId) {
    setAwaitedRevisionId(pendingRevisionId)
  }

  const onFeedbackFinished = useCallback(() => void reload(), [reload])
  const feedback = useAiRequestPolling(
    latest && isActive(latest.feedbackRequest) ? latest.feedbackRequest.id : null,
    onFeedbackFinished,
  )
  const onFollowUpFinished = useCallback(
    (detail: { status: string }) => {
      if (detail.status !== 'SUCCEEDED') {
        setError('꼬리질문을 만들지 못했어요. 다시 요청하거나 질문을 넘겨 보세요.')
      }
      void reload()
    },
    [reload],
  )
  const followUp = useAiRequestPolling(followUpPending ? practice.followUpRequest!.id : null, onFollowUpFinished)

  /** 요청 하나를 실행하고 상세를 다시 읽는다. 409·404 등은 안내 문구로 바꾼다. */
  const run = async (action: () => Promise<unknown>): Promise<boolean> => {
    setBusy(true)
    setError(null)
    try {
      await action()
      return true
    } catch (caught) {
      setError(messageOf(caught))
      return false
    } finally {
      await reload()
      setBusy(false)
    }
  }

  const submit = async (content: string) => {
    const succeeded = await run(() => practiceApi.submitAnswer(current.id, clientRequestId.current, content))
    if (succeeded) {
      clientRequestId.current = crypto.randomUUID()
      setMode('view')
    }
    return succeeded
  }

  const revise = async (attempt: AnswerAttempt, content: string) => {
    const succeeded = await run(() =>
      practiceApi.revise(attempt.id, attempt.currentRevision.revisionNo, content),
    )
    setMode('view')
    return succeeded
  }

  const complete = async () => {
    if (await run(() => practiceApi.complete(practice.id))) {
      navigate(`/practices/${practice.id}`, { replace: true })
    }
  }

  const showForm = !latest || mode === 'reanswer'
  const feedbackReady = latest?.feedback != null

  return (
    <div className={styles.page}>
      {previous.length > 0 && <PreviousQuestions questions={previous} />}

      <Card key={current.id} reveal className={styles.questionCard} aria-label="현재 질문">
        <div className={styles.badgeRow}>
          <Badge>질문 {current.orderNo}</Badge>
          {current.sourceKind === 'FOLLOW_UP' && <Badge tone="neutral">내 답변에서 이어진 질문</Badge>}
          {latest && (
            <Badge key={latest.attemptNo} tone="neutral" pop>
              {latest.attemptNo}번째 답변
            </Badge>
          )}
        </div>
        <h1 className={styles.question}>{current.content}</h1>
      </Card>

      {error && <ErrorBanner>{error}</ErrorBanner>}

      {showForm ? (
        <AnswerForm
          submitLabel={latest ? '다시 답하기' : '답변 제출'}
          busy={busy}
          onSubmit={submit}
          onCancel={latest ? () => setMode('view') : undefined}
        />
      ) : mode === 'edit' ? (
        <AnswerForm
          initialValue={latest.currentRevision.content}
          submitLabel="고친 답변으로 피드백 받기"
          busy={busy}
          onSubmit={(content) => revise(latest, content)}
          onCancel={() => setMode('view')}
        />
      ) : (
        <>
          <Card aria-label="내 답변">
            <span className={styles.label}>내 답변</span>
            <p className={styles.answerText}>{latest.currentRevision.content}</p>
            <div className={styles.secondaryActions}>
              <Button variant="ghost" size="sm" onClick={() => setMode('edit')} disabled={busy || followUpPending}>
                답변 고치기
              </Button>
            </div>
          </Card>
          <FeedbackView
            attempt={latest}
            slow={feedback.slow}
            busy={busy}
            animate={awaitedRevisionId === latest.currentRevision.id}
            onRetry={() => void run(() => practiceApi.retryFeedback(latest.currentRevision.id))}
          />
        </>
      )}

      {followUpPending && (
        <Card>
          <Pending>{followUp.slow ? '다음 질문이 오래 걸리고 있어요.' : '답변을 바탕으로 다음 질문을 만들고 있어요.'}</Pending>
        </Card>
      )}

      {!showForm && mode === 'view' && (
        <div className={styles.actions}>
          {feedbackReady && !followUpPending && (
            <Button
              onClick={() => void run(() => practiceApi.requestFollowUp(practice.id, latest.currentRevision.id))}
              disabled={busy}
            >
              이어서 질문 받기
            </Button>
          )}
          {feedbackReady && !followUpPending && (
            <Button variant="secondary" onClick={() => setMode('reanswer')} disabled={busy}>
              다시 답하기
            </Button>
          )}
        </div>
      )}

      <div className={styles.secondaryActions}>
        {!followUpPending && (
          <Button variant="ghost" size="sm" onClick={() => void run(() => practiceApi.skip(current.id))} disabled={busy}>
            질문 넘기기
          </Button>
        )}
        <Button variant="ghost" size="sm" onClick={() => void complete()} disabled={busy}>
          연습 마치기
        </Button>
      </div>
    </div>
  )
}

function PreviousQuestions({ questions }: { questions: Question[] }) {
  return (
    <details className={styles.previousCard}>
      <summary className={`${styles.label} ${styles.summary}`}>
        <span className={styles.chevron} aria-hidden="true">
          ›
        </span>
        지난 질문 {questions.length}개
      </summary>
      <ol className={styles.previous}>
        {questions.map((question) => (
          <li key={question.id} className={styles.previousItem}>
            {question.content}
            {question.skipped && <span className={styles.caption}> · 넘김</span>}
          </li>
        ))}
      </ol>
    </details>
  )
}

/** 마친 연습의 기록. 질문마다 모든 답변 시도와, 각 시도의 현재 버전에 맞는 피드백을 보여 준다. */
function RecordView({ practice }: { practice: Practice }) {
  return (
    <div className={styles.page}>
      <div>
        <h1 className={styles.title}>연습 기록</h1>
        <p className={styles.caption}>
          {formatDateTime(practice.createdAt)} 시작
          {practice.completedAt && ` · ${formatDateTime(practice.completedAt)} 마침`}
        </p>
      </div>
      {practice.questions.map((question) => (
        <Card key={question.id} className={styles.questionCard}>
          <div className={styles.badgeRow}>
            <Badge>질문 {question.orderNo}</Badge>
            {question.skipped && <Badge tone="neutral">넘긴 질문</Badge>}
          </div>
          <h2 className={styles.question}>{question.content}</h2>
          {question.attempts.map((attempt) => (
            <div key={attempt.id} className={styles.attempt}>
              <span className={styles.label}>
                {attempt.attemptNo}번째 답변
                {attempt.currentRevision.revisionNo > 1 && ' · 고친 답변'}
              </span>
              <p className={styles.answerText}>{attempt.currentRevision.content}</p>
              <FeedbackView attempt={attempt} />
            </div>
          ))}
        </Card>
      ))}
      <Link to="/">새 연습 시작하기</Link>
    </div>
  )
}
