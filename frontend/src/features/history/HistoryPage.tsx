import { useCallback, useEffect, useState } from 'react'
import { CalendarDays, ChevronRight, Trash2 } from 'lucide-react'
import { Link } from 'react-router'
import { Badge } from '../../shared/ui/Badge'
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog'
import { EmptyState } from '../../shared/ui/EmptyState'
import { ErrorBanner } from '../../shared/ui/ErrorBanner'
import { IconButton } from '../../shared/ui/IconButton'
import { useToast } from '../../shared/ui/toastContext'
import { formatDateTime } from '../practice/labels'
import { practiceApi, type PracticeSummary } from '../practice/practiceApi'
import styles from './HistoryPage.module.css'

/** 연습별 기록 목록. 하나를 골라 상세(진행 중이면 이어하기)로 간다. */
export function HistoryPage() {
  const [items, setItems] = useState<PracticeSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  // 닫히는 움직임 동안에도 안내 문구가 남도록 대상과 열림을 따로 둔다.
  const [target, setTarget] = useState<PracticeSummary | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [removing, setRemoving] = useState(false)
  const toast = useToast()

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
    setRemoving(true)
    try {
      await practiceApi.remove(item.id)
      setConfirming(false)
      toast('연습 기록을 삭제했어요.', 'success')
      await load()
    } catch {
      setConfirming(false)
      toast('삭제하지 못했어요. 잠시 후 다시 시도해 주세요.', 'error')
    } finally {
      setRemoving(false)
    }
  }

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>연습 기록</h1>
      {error && <ErrorBanner>{error}</ErrorBanner>}
      {items?.length === 0 && (
        <EmptyState
          icon={CalendarDays}
          title="아직 연습 기록이 없어요."
          description="프로젝트 경험 질문 하나에 답해 보면 여기에 쌓여요."
          action={
            <Link to="/" className={styles.startLink}>
              연습 시작하기
            </Link>
          }
        />
      )}
      <ul className={styles.list}>
        {items?.map((item) => (
          <li key={item.id} className={styles.item}>
            <Link to={`/practices/${item.id}`} className={styles.link}>
              <span className={styles.date}>{formatDateTime(item.createdAt)}</span>
              <span className={styles.meta}>
                <Badge tone={item.canProceed ? 'primary' : 'neutral'}>{item.canProceed ? '이어하기' : '마침'}</Badge>
                질문 {item.questionCount}개
                <ChevronRight className={styles.chevron} size={18} aria-hidden="true" />
              </span>
            </Link>
            <IconButton
              icon={Trash2}
              tone="danger"
              label={`${formatDateTime(item.createdAt)} 연습 삭제`}
              onClick={() => {
                setTarget(item)
                setConfirming(true)
              }}
            />
          </li>
        ))}
      </ul>
      <ConfirmDialog
        open={confirming}
        title="연습 기록을 삭제할까요?"
        confirmLabel="삭제"
        tone="danger"
        busy={removing}
        onConfirm={() => target && void remove(target)}
        onCancel={() => setConfirming(false)}
      >
        {target && `${formatDateTime(target.createdAt)} 연습의 질문·답변·피드백을 다시 볼 수 없어요.`}
      </ConfirmDialog>
    </div>
  )
}
