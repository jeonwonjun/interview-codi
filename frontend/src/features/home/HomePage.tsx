import { useEffect, useState } from 'react'
import { ChevronRight, FileText, MessageSquareText } from 'lucide-react'
import { Link, useNavigate } from 'react-router'
import { Button } from '../../shared/ui/Button'
import { ErrorBanner } from '../../shared/ui/ErrorBanner'
import { formatDateTime } from '../practice/labels'
import { practiceApi, type PracticeSummary } from '../practice/practiceApi'
import styles from './HomePage.module.css'

/** 로그인 직후 화면. 진행 중인 연습을 이어가거나 새로 시작한다. 이력서 연습은 아직 준비 중이다. */
export function HomePage() {
  const navigate = useNavigate()
  const [inProgress, setInProgress] = useState<PracticeSummary[]>([])
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let ignore = false
    practiceApi
      .list()
      .then((items) => {
        if (!ignore) {
          setInProgress(items.filter((item) => item.canProceed).slice(0, 3))
        }
      })
      .catch(() => {
        // 이어하기 목록은 없어도 새 연습은 시작할 수 있다.
      })
    return () => {
      ignore = true
    }
  }, [])

  const start = async () => {
    setStarting(true)
    setError(null)
    try {
      const practice = await practiceApi.create()
      navigate(`/practices/${practice.id}`)
    } catch {
      setError('연습을 시작하지 못했어요. 잠시 후 다시 시도해 주세요.')
      setStarting(false)
    }
  }

  return (
    <>
      <section className={styles.header}>
        <h1 className={styles.title}>어떻게 연습해 볼까요?</h1>
        <p className={styles.lead}>처음이라면 자료 없이 바로 시작해 보세요. 이력서는 나중에 더해도 돼요.</p>
      </section>

      {error && <ErrorBanner>{error}</ErrorBanner>}

      <div className={styles.choices}>
        {inProgress.length > 0 && (
          <section className={styles.choice} aria-label="이어하기">
            <h2 className={styles.choiceTitle}>하던 연습 이어하기</h2>
            <ul className={styles.resumeList}>
              {inProgress.map((item) => (
                <li key={item.id}>
                  <Link to={`/practices/${item.id}`} className={styles.resumeLink}>
                    <span>
                      {formatDateTime(item.createdAt)} 시작 · 질문 {item.questionCount}개
                    </span>
                    <ChevronRight size={18} strokeWidth={2} aria-hidden="true" />
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        )}

        <section className={styles.choice}>
          <h2 className={styles.choiceTitle}>
            <MessageSquareText size={20} strokeWidth={2} aria-hidden="true" />
            자료 없이 바로 연습
          </h2>
          <p className={styles.choiceBody}>내가 만든 프로젝트에 대한 질문으로 시작해요. 답변에 맞춰 꼬리질문이 이어져요.</p>
          <Button block onClick={() => void start()} disabled={starting}>
            연습 시작하기
          </Button>
        </section>

        <section className={styles.choice}>
          <div className={styles.choiceHeader}>
            <h2 className={styles.choiceTitle}>
              <FileText size={20} strokeWidth={2} aria-hidden="true" />
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
    </>
  )
}
