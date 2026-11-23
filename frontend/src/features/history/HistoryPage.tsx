import { useState } from 'react'
import { CalendarDays, ChevronRight, Trash2 } from 'lucide-react'
import { Link } from 'react-router'
import { Badge } from '../../shared/ui/Badge'
import { Card } from '../../shared/ui/Card'
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog'
import { EmptyState } from '../../shared/ui/EmptyState'
import { ErrorBanner } from '../../shared/ui/ErrorBanner'
import { IconButton } from '../../shared/ui/IconButton'
import { useToast } from '../../shared/ui/toastContext'
import { formatDateTime } from '../practice/labels'
import { practiceApi, type PracticeSummary } from '../practice/practiceApi'
import { dateKey, groupByDay, parseDateKey, streak } from '../records/calendar'
import { MonthCalendar } from '../records/MonthCalendar'
import { usePracticeSummaries } from '../records/usePracticeSummaries'
import styles from './HistoryPage.module.css'

const dayTitle = new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' })
const timeLabel = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit' })

/**
 * 달력 기록. 답변한 날에 점을 찍고, 날짜를 누르면 그날 연습을 보여 준다(uibowl 캘린더 패턴).
 * 시작만 하고 답하지 않은 연습은 점을 찍지 않지만 그날 목록에는 보여 줘서 지울 수 있게 한다.
 */
export function HistoryPage() {
  const { state, reload } = usePracticeSummaries()
  // 화면을 연 날을 오늘로 본다. 렌더마다 시계를 읽지 않게 처음 한 번만 잡는다.
  const [today] = useState(() => new Date())
  const [month, setMonth] = useState(() => new Date(today.getFullYear(), today.getMonth(), 1))
  const [selected, setSelected] = useState<string | null>(null)
  // 닫히는 움직임 동안에도 안내 문구가 남도록 대상과 열림을 따로 둔다.
  const [target, setTarget] = useState<PracticeSummary | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [removing, setRemoving] = useState(false)
  const toast = useToast()

  if (state.status === 'loading') {
    return null
  }
  if (state.status === 'error') {
    return <ErrorBanner>기록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</ErrorBanner>
  }

  const items = state.items
  const byDay = groupByDay(items)
  const answeredByDay = groupByDay(items.filter((item) => item.answerCount > 0))
  const counts = new Map([...answeredByDay].map(([key, list]) => [key, list.length]))
  // 고르지 않았으면 오늘, 오늘 기록이 없으면 가장 최근에 연습한 날을 보여 준다.
  const latestKey = items.length > 0 ? dateKey(new Date(items[0].createdAt)) : null
  const selectedKey = selected ?? (byDay.has(dateKey(today)) || !latestKey ? dateKey(today) : latestKey)
  const dayItems = byDay.get(selectedKey) ?? []
  const monthPrefix = dateKey(month).slice(0, 7)
  const monthDays = [...answeredByDay.keys()].filter((key) => key.startsWith(monthPrefix)).length
  const days = streak(new Set(answeredByDay.keys()), today)

  const select = (key: string) => {
    setSelected(key)
    const date = parseDateKey(key)
    setMonth(new Date(date.getFullYear(), date.getMonth(), 1))
  }

  const remove = async (item: PracticeSummary) => {
    setRemoving(true)
    try {
      await practiceApi.remove(item.id)
      setConfirming(false)
      toast('연습 기록을 삭제했어요.', 'success')
      await reload()
    } catch {
      setConfirming(false)
      toast('삭제하지 못했어요. 잠시 후 다시 시도해 주세요.', 'error')
    } finally {
      setRemoving(false)
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.heading}>
        <h1 className={styles.title}>연습 기록</h1>
        {items.length > 0 && (
          <p className={styles.stats}>
            {month.getMonth() + 1}월 <strong>{monthDays}일</strong> 연습
            {days > 1 && (
              <>
                {' '}
                · 연속 <strong>{days}일</strong>
              </>
            )}
          </p>
        )}
      </div>

      {items.length === 0 ? (
        <EmptyState
          icon={CalendarDays}
          title="아직 연습 기록이 없어요."
          description="프로젝트 경험 질문 하나에 답해 보면 달력에 표시돼요."
          action={
            <Link to="/" className={styles.startLink}>
              연습 시작하기
            </Link>
          }
        />
      ) : (
        <div className={styles.layout}>
          <Card>
            <MonthCalendar
              month={month}
              onMonthChange={setMonth}
              counts={counts}
              selected={selectedKey}
              onSelect={select}
              today={today}
            />
          </Card>

          <Card key={selectedKey} reveal className={styles.day}>
            <h2 className={styles.dayTitle}>{dayTitle.format(parseDateKey(selectedKey))}</h2>
            {dayItems.length === 0 ? (
              <p className={styles.empty}>
                이 날은 연습하지 않았어요.
                {selectedKey === dateKey(today) && (
                  <>
                    {' '}
                    <Link to="/">지금 한 질문 답해 볼까요?</Link>
                  </>
                )}
              </p>
            ) : (
              <ul className={styles.list}>
                {dayItems.map((item) => (
                  <li key={item.id} className={styles.item}>
                    <Link to={`/practices/${item.id}`} className={styles.link}>
                      <span className={styles.row}>
                        <span className={styles.time}>{timeLabel.format(new Date(item.createdAt))}</span>
                        <Badge tone={item.canProceed ? 'primary' : 'neutral'}>
                          {item.canProceed ? '이어하기' : '마침'}
                        </Badge>
                      </span>
                      <span className={styles.meta}>
                        질문 {item.questionCount}개 ·{' '}
                        {item.answerCount > 0 ? `답변 ${item.answerCount}번` : '아직 답하지 않음'}
                      </span>
                    </Link>
                    <ChevronRight className={styles.chevron} size={18} aria-hidden="true" />
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
            )}
          </Card>
        </div>
      )}

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
