import { describe, expect, it } from 'vitest'
import { dateKey, groupByDay, monthGrid, streak } from './calendar'

describe('달력 계산', () => {
  it('2026년 11월은 일요일에 시작해 다섯 주로 그린다', () => {
    const weeks = monthGrid(2026, 10)

    expect(weeks).toHaveLength(5)
    expect(weeks[0][0]?.getDate()).toBe(1)
    expect(weeks[4].filter(Boolean)).toHaveLength(2)
  })

  it('서버 시각을 브라우저 날짜로 묶는다', () => {
    const local = new Date(2026, 10, 18, 23, 30)
    const groups = groupByDay([{ at: local.toISOString(), id: 1 }], (item) => item.at)

    expect([...groups.keys()]).toEqual(['2026-11-18'])
  })

  it('오늘 아직 안 했으면 어제까지 이어진 날을 센다', () => {
    const today = new Date(2026, 10, 19)
    const days = new Set(['2026-11-16', '2026-11-17', '2026-11-18'])

    expect(streak(days, today)).toBe(3)
    expect(streak(new Set([...days, dateKey(today)]), today)).toBe(4)
    expect(streak(new Set(['2026-11-16']), today)).toBe(0)
  })
})
