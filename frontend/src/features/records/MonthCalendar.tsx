import { ChevronLeft, ChevronRight } from 'lucide-react'
import { IconButton } from '../../shared/ui/IconButton'
import { addMonths, dateKey, monthGrid } from './calendar'
import styles from './MonthCalendar.module.css'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

type Props = {
  month: Date
  onMonthChange: (month: Date) => void
  /** 날짜 열쇠(YYYY-MM-DD)별 연습 수. 0이거나 없으면 연습 안 한 날이다. */
  counts: Map<string, number>
  selected?: string
  onSelect?: (key: string) => void
  /** sm은 웹 오른쪽 칸에 넣는 작은 달력. 연습한 날을 옅은 색 칸으로 보여 준다. */
  size?: 'md' | 'sm'
  today: Date
}

/** 월 달력. 연습한 날에 점(md)이나 옅은 칸(sm)을 표시하고, 날짜를 누르면 그날을 고른다. */
export function MonthCalendar({ month, onMonthChange, counts, selected, onSelect, size = 'md', today }: Props) {
  const weeks = monthGrid(month.getFullYear(), month.getMonth())
  const todayKey = dateKey(today)
  const title = `${month.getFullYear()}년 ${month.getMonth() + 1}월`

  return (
    <div className={`${styles.calendar} ${styles[size]}`}>
      <div className={styles.header}>
        <h2 className={styles.title} aria-live="polite">
          {title}
        </h2>
        <div className={styles.nav}>
          <IconButton icon={ChevronLeft} label="이전 달" onClick={() => onMonthChange(addMonths(month, -1))} />
          <IconButton icon={ChevronRight} label="다음 달" onClick={() => onMonthChange(addMonths(month, 1))} />
        </div>
      </div>
      <table className={styles.grid}>
        <thead>
          <tr>
            {WEEKDAYS.map((weekday) => (
              <th key={weekday} scope="col" className={styles.weekday}>
                {weekday}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {weeks.map((week, row) => (
            <tr key={row}>
              {week.map((date, column) => {
                if (!date) {
                  return <td key={column} />
                }
                const key = dateKey(date)
                const count = counts.get(key) ?? 0
                const classes = [
                  styles.day,
                  count > 0 && styles.practiced,
                  key === todayKey && styles.today,
                  key === selected && styles.selected,
                ]
                  .filter(Boolean)
                  .join(' ')
                const label = `${date.getMonth() + 1}월 ${date.getDate()}일, ${count > 0 ? `연습 ${count}번` : '연습 없음'}`
                return (
                  <td key={column}>
                    {onSelect ? (
                      <button
                        type="button"
                        className={classes}
                        aria-label={label}
                        aria-pressed={key === selected}
                        onClick={() => onSelect(key)}
                      >
                        {date.getDate()}
                      </button>
                    ) : (
                      <span className={classes} aria-label={label} role="img">
                        {date.getDate()}
                      </span>
                    )}
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
