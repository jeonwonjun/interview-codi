import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Button } from '../../shared/ui/Button'
import { ErrorBanner } from '../../shared/ui/ErrorBanner'
import { formatDateTime } from '../practice/labels'
import { practiceApi, type PracticeSummary } from '../practice/practiceApi'
import styles from './HistoryPage.module.css'

/** 연습별 기록 목록. 하나를 골라 상세(진행 중이면 이어하기)로 간다. */
export function HistoryPage() {
  const [items, setItems] = useState<PracticeSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setItems(await practiceApi.list())
    } catch {
      setError('기록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.')
    }
  }, [])

  useEffect(() => {
    void Promise.resolve().then(load)
  }, [load])

  const remove = async (item: PracticeSummary) => {
    if (!window.confirm(`${formatDateTime(item.createdAt)} 연습을 삭제할까요? 삭제한 기록은 다시 볼 수 없어요.`)) {
      return
    }
    try {
      await practiceApi.remove(item.id)
      await load()
    } catch {
      setError('삭제하지 못했어요. 잠시 후 다시 시도해 주세요.')
    }
  }

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>연습 기록</h1>
      {error && <ErrorBanner>{error}</ErrorBanner>}
      {items?.length === 0 && <p className={styles.empty}>아직 연습 기록이 없어요.</p>}
      <ul className={styles.list}>
        {items?.map((item) => (
          <li key={item.id} className={styles.item}>
            <Link to={`/practices/${item.id}`} className={styles.link}>
              <span className={styles.date}>{formatDateTime(item.createdAt)}</span>
              <span className={styles.meta}>
                <span className={item.canProceed ? styles.badge : styles.badgeMuted}>
                  {item.canProceed ? '이어하기' : '마침'}
                </span>
                질문 {item.questionCount}개
              </span>
            </Link>
            <Button variant="ghost" onClick={() => void remove(item)} aria-label={`${formatDateTime(item.createdAt)} 연습 삭제`}>
              삭제
            </Button>
          </li>
        ))}
      </ul>
    </div>
  )
}
