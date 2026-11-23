import { Check, CircleDot, SkipForward } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Card } from '../../shared/ui/Card'
import { activityAt, dateKey, groupByDay, streak } from '../records/calendar'
import { MonthCalendar } from '../records/MonthCalendar'
import { usePracticeSummaries } from '../records/usePracticeSummaries'
import type { Practice } from './practiceApi'
import styles from './PracticeAside.module.css'

/**
 * 웹 연습 화면의 오른쪽 칸. 이번 달 연습한 날과 이번 연습의 질문 흐름을 보여 준다.
 * 답변 수가 바뀌면 기록을 다시 읽어 오늘 칸을 채운다.
 */
export function PracticeAside({ practice }: { practice: Practice }) {
  const { state, reload } = usePracticeSummaries()
  // 화면을 연 날을 오늘로 본다. 렌더마다 시계를 읽지 않게 처음 한 번만 잡는다.
  const [today] = useState(() => new Date())
  const [month, setMonth] = useState(today)
  const answerTotal = practice.questions.reduce((sum, question) => sum + question.attempts.length, 0)

  useEffect(() => {
    if (answerTotal > 0) {
      void reload()
    }
  }, [answerTotal, reload])

  const answered = state.status === 'loaded' ? state.items.filter((item) => item.answerCount > 0) : []
  const byDay = groupByDay(answered, activityAt)
  const counts = new Map([...byDay].map(([key, items]) => [key, items.length]))
  const thisMonth = [...byDay.keys()].filter((key) => key.startsWith(dateKey(today).slice(0, 7))).length
  const days = streak(new Set(byDay.keys()), today)

  return (
    <>
      <Card>
        <MonthCalendar size="sm" month={month} onMonthChange={setMonth} counts={counts} today={today} />
        <p className={styles.stats}>
          이번 달 <strong>{thisMonth}일</strong> 연습
          {days > 1 && (
            <>
              {' '}
              · 연속 <strong>{days}일</strong>
            </>
          )}
        </p>
      </Card>
      <Card>
        <h2 className={styles.heading}>이번 연습</h2>
        <ol className={styles.questions}>
          {practice.questions.map((question) => {
            const Icon = question.current ? CircleDot : question.skipped ? SkipForward : Check
            const state = question.current ? '지금 질문' : question.skipped ? '넘긴 질문' : '답한 질문'
            return (
              <li key={question.id} className={question.current ? styles.current : undefined}>
                <Icon size={16} aria-label={state} className={styles.icon} />
                <span>{question.content}</span>
              </li>
            )
          })}
        </ol>
      </Card>
    </>
  )
}
