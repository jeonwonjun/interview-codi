import { useCallback, useRef, useState } from 'react'
import {
  ChevronRight,
  CornerDownRight,
  Flag,
  Keyboard,
  MessageCirclePlus,
  Mic,
  Pencil,
  RotateCcw,
  SkipForward,
  Volume2,
  VolumeX,
} from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../shared/api/client'
import { PageColumns } from '../../shared/ui/AppShell'
import { Badge } from '../../shared/ui/Badge'
import { Button } from '../../shared/ui/Button'
import { Card } from '../../shared/ui/Card'
import { ErrorBanner } from '../../shared/ui/ErrorBanner'
import { IconButton } from '../../shared/ui/IconButton'
import { Pending } from '../../shared/ui/Pending'
import { AnswerForm } from './AnswerForm'
import { FeedbackView } from './FeedbackView'
import { formatDateTime } from './labels'
import { PracticeAside } from './PracticeAside'
import {
  type AnswerAttempt,
  hasRevision,
  isActive,
  type Practice,
  practiceApi,
  type Question,
  type TranscribedAttempt,
} from './practiceApi'
import styles from './Practice.module.css'
import { useAiRequestPolling } from './useAiRequestPolling'
import { usePractice } from './usePractice'
import { isRecordingSupported } from './voice/recording'
import { RecordingPlayer } from './voice/RecordingPlayer'
import { useQuestionSpeech } from './voice/useQuestionSpeech'
import type { RecordedAudio } from './voice/useRecorder'
import { VoiceAnswer } from './voice/VoiceAnswer'

export function PracticePage() {
  const { practiceId } = useParams()
  return <PracticeScreen key={practiceId} practiceId={Number(practiceId)} />
}

/** 연습 하나의 화면. 진행 중이면 답하는 화면, 마쳤으면 기록 화면이다. 첫 화면도 이 화면을 그대로 쓴다. */
export function PracticeScreen({ practiceId }: { practiceId: number }) {
  const { state, reload } = usePractice(practiceId)

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
  return (
    <PageColumns aside={<PracticeAside practice={state.practice} />}>
      {state.practice.canProceed ? (
        <InProgressView practice={state.practice} reload={reload} />
      ) : (
        <RecordView practice={state.practice} />
      )}
    </PageColumns>
  )
}

type Mode = 'view' | 'reanswer' | 'edit'
type InputMode = 'voice' | 'text'

/** 오류 코드별 안내. 그 밖의 오류는 서버가 준 문구를 쓴다. */
function messageOf(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return '서버에 연결하지 못했어요. 답변은 그대로 있어요. 잠시 후 다시 시도해 주세요.'
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

const UNAVAILABLE_MESSAGE = {
  denied: '마이크 사용이 허락되지 않아 글로 답하도록 바꿨어요. 브라우저 설정에서 허락하면 다시 말로 답할 수 있어요.',
  'no-device': '마이크를 찾지 못해 글로 답하도록 바꿨어요.',
  unsupported: '이 브라우저에서는 녹음할 수 없어 글로 답하도록 바꿨어요.',
} as const

function InProgressView({ practice, reload }: { practice: Practice; reload: () => Promise<void> }) {
  const navigate = useNavigate()
  const [mode, setMode] = useState<Mode>('view')
  // 음성이 기본이고 텍스트는 대체 수단이다(기획 3절). 녹음할 수 없는 브라우저는 처음부터 글로 답한다.
  const [inputMode, setInputMode] = useState<InputMode>(() => (isRecordingSupported() ? 'voice' : 'text'))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // 같은 제출을 다시 보낼 때(네트워크 오류 등) 같은 값을 써서 서버가 한 번만 저장하게 한다.
  const clientRequestId = useRef(crypto.randomUUID())

  const current = practice.questions.find((question) => question.current) ?? practice.questions.at(-1)!
  const previous = practice.questions.filter((question) => question.id !== current.id)
  const latest = current.attempts.at(-1)
  const transcribed = hasRevision(latest)
  const transcribing = latest != null && !transcribed && isActive(latest.transcriptionRequest)
  const followUpPending = isActive(practice.followUpRequest)
  const speech = useQuestionSpeech(current.content)
  // 이 화면에서 기다리던 피드백만 도착할 때 순서대로 보여 준다. 새로 열었을 때 이미 있던 피드백은 바로 보여 준다.
  const [awaitedRevisionId, setAwaitedRevisionId] = useState<number | null>(null)
  const pendingRevisionId = transcribed && isActive(latest.feedbackRequest) ? latest.currentRevision.id : null
  if (pendingRevisionId != null && pendingRevisionId !== awaitedRevisionId) {
    setAwaitedRevisionId(pendingRevisionId)
  }

  const onRequestFinished = useCallback(() => void reload(), [reload])
  const feedback = useAiRequestPolling(
    transcribed && isActive(latest.feedbackRequest) ? latest.feedbackRequest.id : null,
    onRequestFinished,
  )
  const transcription = useAiRequestPolling(transcribing ? latest.transcriptionRequest!.id : null, onRequestFinished)
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

  const onMicUnavailable = useCallback((reason: keyof typeof UNAVAILABLE_MESSAGE) => {
    setInputMode('text')
    setError(UNAVAILABLE_MESSAGE[reason])
  }, [])

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

  const afterSubmit = (succeeded: boolean) => {
    if (succeeded) {
      clientRequestId.current = crypto.randomUUID()
      setMode('view')
    }
    return succeeded
  }

  const submitText = async (content: string) =>
    afterSubmit(await run(() => practiceApi.submitAnswer(current.id, clientRequestId.current, content)))

  const submitVoice = async (audio: RecordedAudio) =>
    afterSubmit(
      await run(() => practiceApi.submitVoice(current.id, clientRequestId.current, audio.blob, audio.durationMs)),
    )

  const revise = async (attempt: TranscribedAttempt, content: string) => {
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

  const answerAgain = (next: InputMode) => {
    setInputMode(next)
    setMode('reanswer')
  }

  const showForm = !latest || mode === 'reanswer'
  const feedbackReady = transcribed && latest.feedback != null
  const submitLabel = latest ? '다시 답하기' : '답변 제출'
  const cancel = latest ? () => setMode('view') : undefined

  return (
    <div className={styles.page}>
      {previous.length > 0 && <PreviousQuestions questions={previous} />}

      <Card key={current.id} reveal className={styles.questionCard} aria-label="현재 질문">
        <div className={styles.questionHead}>
          <div className={styles.badgeRow}>
            <Badge>질문 {current.orderNo}</Badge>
            {current.sourceKind === 'FOLLOW_UP' && (
              <Badge tone="neutral" icon={CornerDownRight}>
                이어진 질문
              </Badge>
            )}
            {latest && (
              <Badge key={latest.attemptNo} tone="neutral" pop>
                {latest.attemptNo}번째 답변
              </Badge>
            )}
          </div>
          {speech.available && (
            <IconButton
              icon={speech.enabled ? Volume2 : VolumeX}
              label={speech.enabled ? '질문 읽어 주기 끄기' : '질문 읽어 주기 켜기'}
              aria-pressed={speech.enabled}
              onClick={speech.toggle}
            />
          )}
        </div>
        <h1 className={styles.question}>{current.content}</h1>
      </Card>

      {error && <ErrorBanner>{error}</ErrorBanner>}

      {showForm ? (
        inputMode === 'voice' ? (
          <VoiceAnswer
            submitLabel="이 녹음 제출"
            busy={busy}
            onSubmit={submitVoice}
            onUseText={() => setInputMode('text')}
            onUnavailable={onMicUnavailable}
            onRecordingStart={speech.stop}
            onCancel={cancel}
          />
        ) : (
          <AnswerForm
            submitLabel={submitLabel}
            busy={busy}
            onSubmit={submitText}
            onCancel={cancel}
            secondaryAction={
              isRecordingSupported() && (
                <Button variant="ghost" icon={Mic} onClick={() => setInputMode('voice')} disabled={busy}>
                  말로 답하기
                </Button>
              )
            }
          />
        )
      ) : mode === 'edit' && transcribed ? (
        <AnswerForm
          initialValue={latest.currentRevision.content}
          submitLabel="고친 답변으로 피드백 받기"
          busy={busy}
          onSubmit={(content) => revise(latest, content)}
          onCancel={() => setMode('view')}
        />
      ) : transcribed ? (
        <>
          <Card aria-label="내 답변">
            <span className={styles.label}>{latest.inputMode === 'VOICE' ? '내 답변 · 녹음을 글로 옮긴 문구' : '내 답변'}</span>
            {latest.recording && <RecordingPlayer answerId={latest.id} durationMs={latest.recording.durationMs} />}
            <p className={styles.answerText}>{latest.currentRevision.content}</p>
            <div className={styles.secondaryActions}>
              <Button
                variant="ghost"
                size="sm"
                icon={Pencil}
                onClick={() => setMode('edit')}
                disabled={busy || followUpPending}
              >
                {latest.inputMode === 'VOICE' ? '글로 옮긴 문구 고치기' : '답변 고치기'}
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
      ) : (
        latest && (
          <TranscriptionStatus
            attempt={latest}
            active={transcribing}
            slow={transcription.slow}
            busy={busy}
            onRetry={() => void run(() => practiceApi.retryTranscription(latest.recording!.id))}
            onAnswerAgain={answerAgain}
          />
        )
      )}

      {followUpPending && (
        <Card>
          <Pending>{followUp.slow ? '다음 질문이 오래 걸리고 있어요.' : '답변을 바탕으로 다음 질문을 만들고 있어요.'}</Pending>
        </Card>
      )}

      {!showForm && mode === 'view' && feedbackReady && !followUpPending && (
        <div className={styles.actions}>
          <Button
            icon={MessageCirclePlus}
            onClick={() => void run(() => practiceApi.requestFollowUp(practice.id, latest.currentRevision.id))}
            disabled={busy}
          >
            이어서 질문 받기
          </Button>
          <Button variant="secondary" icon={RotateCcw} onClick={() => setMode('reanswer')} disabled={busy}>
            다시 답하기
          </Button>
        </div>
      )}

      <div className={styles.secondaryActions}>
        {!followUpPending && (
          <Button
            variant="ghost"
            size="sm"
            icon={SkipForward}
            onClick={() => void run(() => practiceApi.skip(current.id))}
            disabled={busy}
          >
            질문 넘기기
          </Button>
        )}
        <Button variant="ghost" size="sm" icon={Flag} onClick={() => void complete()} disabled={busy}>
          연습 마치기
        </Button>
      </div>
    </div>
  )
}

/**
 * 글로 옮기는 중이거나 옮기지 못한 음성 시도. 옮기지 못했어도 녹음은 남아 있어 다시 녹음하라고 요구하지 않는다(기획 7절).
 */
function TranscriptionStatus({
  attempt,
  active,
  slow,
  busy,
  onRetry,
  onAnswerAgain,
}: {
  attempt: AnswerAttempt
  active: boolean
  slow: boolean
  busy: boolean
  onRetry: () => void
  onAnswerAgain: (inputMode: InputMode) => void
}) {
  return (
    <Card aria-label="내 답변">
      <span className={styles.label}>내 답변</span>
      {attempt.recording && <RecordingPlayer answerId={attempt.id} durationMs={attempt.recording.durationMs} />}
      {active ? (
        <Pending>{slow ? '글로 옮기는 데 오래 걸리고 있어요.' : '녹음을 글로 옮기고 있어요.'}</Pending>
      ) : (
        <>
          <p className={styles.observation}>녹음을 글로 옮기지 못했어요. 녹음은 그대로 남아 있어요.</p>
          <div className={styles.actions}>
            {attempt.recording && (
              <Button icon={RotateCcw} onClick={onRetry} loading={busy}>
                다시 글로 옮기기
              </Button>
            )}
            <Button variant="secondary" icon={Mic} onClick={() => onAnswerAgain('voice')} disabled={busy}>
              다시 녹음하기
            </Button>
            <Button variant="ghost" icon={Keyboard} onClick={() => onAnswerAgain('text')} disabled={busy}>
              글로 답하기
            </Button>
          </div>
        </>
      )}
    </Card>
  )
}

function PreviousQuestions({ questions }: { questions: Question[] }) {
  return (
    <details className={styles.previousCard}>
      <summary className={`${styles.label} ${styles.summary}`}>
        <ChevronRight className={styles.chevron} size={16} aria-hidden="true" />
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
                {attempt.inputMode === 'VOICE' && ' · 음성'}
                {hasRevision(attempt) && attempt.currentRevision.revisionNo > 1 && ' · 고친 답변'}
              </span>
              {attempt.recording && <RecordingPlayer answerId={attempt.id} durationMs={attempt.recording.durationMs} />}
              {hasRevision(attempt) ? (
                <>
                  <p className={styles.answerText}>{attempt.currentRevision.content}</p>
                  <FeedbackView attempt={attempt} />
                </>
              ) : (
                <p className={styles.caption}>글로 옮기지 못한 녹음이에요.</p>
              )}
            </div>
          ))}
        </Card>
      ))}
      <Link to="/">새 연습 시작하기</Link>
    </div>
  )
}
