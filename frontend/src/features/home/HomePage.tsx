import { useState } from 'react'
import { FileText, MessageSquareText } from 'lucide-react'
import { PageColumns } from '../../shared/ui/AppShell'
import { Button } from '../../shared/ui/Button'
import { ErrorBanner } from '../../shared/ui/ErrorBanner'
import { practiceApi } from '../practice/practiceApi'
import { PracticeScreen } from '../practice/PracticePage'
import { usePracticeSummaries } from '../records/usePracticeSummaries'
import styles from './HomePage.module.css'

/**
 * 첫 화면이 곧 연습 화면이다. 진행 중인 연습이 있으면 가장 최근 것을 바로 이어서 보여 주고,
 * 없으면 시작 버튼을 보여 준다. 시작해도 주소는 그대로 두고 이 자리에서 연습한다.
 * 빈 연습이 쌓이지 않도록 사용자가 시작을 누를 때만 연습을 만든다.
 */
export function HomePage() {
  const { state } = usePracticeSummaries()
  const [startedId, setStartedId] = useState<number | null>(null)

  if (startedId != null) {
    return <PracticeScreen key={startedId} practiceId={startedId} />
  }
  if (state.status === 'loading') {
    return null
  }
  const ongoing = state.status === 'loaded' ? state.items.find((item) => item.canProceed) : undefined
  if (ongoing) {
    return <PracticeScreen key={ongoing.id} practiceId={ongoing.id} />
  }
  return <StartView onStarted={setStartedId} />
}

function StartView({ onStarted }: { onStarted: (practiceId: number) => void }) {
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const start = async () => {
    setStarting(true)
    setError(null)
    try {
      onStarted((await practiceApi.create()).id)
    } catch {
      setError('연습을 시작하지 못했어요. 잠시 후 다시 시도해 주세요.')
      setStarting(false)
    }
  }

  return (
    <PageColumns>
      <section className={styles.header}>
        <h1 className={styles.title}>오늘도 한 질문만 답해 볼까요?</h1>
        <p className={styles.lead}>처음이라면 자료 없이 바로 시작해 보세요. 이력서는 나중에 더해도 돼요.</p>
      </section>

      {error && <ErrorBanner>{error}</ErrorBanner>}

      <div className={styles.choices}>
        <section className={styles.choice}>
          <h2 className={styles.choiceTitle}>
            <MessageSquareText size={20} aria-hidden="true" />
            자료 없이 바로 연습
          </h2>
          <p className={styles.choiceBody}>내가 만든 프로젝트에 대한 질문으로 시작해요. 답변에 맞춰 꼬리질문이 이어져요.</p>
          <Button block onClick={() => void start()} loading={starting}>
            연습 시작하기
          </Button>
        </section>

        <section className={styles.choice}>
          <div className={styles.choiceHeader}>
            <h2 className={styles.choiceTitle}>
              <FileText size={20} aria-hidden="true" />
              이력서로 맞춤 연습
            </h2>
            <span className={styles.badge}>준비 중</span>
          </div>
          <p className={styles.choiceBody}>이력서에 적은 경험을 바탕으로 질문을 만들어요.</p>
          <Button variant="secondary" block disabled>
            이력서 넣기
          </Button>
        </section>
      </div>
    </PageColumns>
  )
}
