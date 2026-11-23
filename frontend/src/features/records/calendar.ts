/** 브라우저 시간대 기준 날짜 열쇠(YYYY-MM-DD). 서버 시각(UTC)을 사용자가 보는 날짜로 묶을 때 쓴다. */
export function dateKey(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

export function parseDateKey(key: string): Date {
  const [year, month, day] = key.split('-').map(Number)
  return new Date(year, month - 1, day)
}

/** 일요일부터 시작하는 주 단위 칸. 그 달이 아닌 칸은 null이다. */
export function monthGrid(year: number, month: number): (Date | null)[][] {
  const first = new Date(year, month, 1)
  const days = new Date(year, month + 1, 0).getDate()
  const cells: (Date | null)[] = Array.from({ length: first.getDay() }, () => null)
  for (let day = 1; day <= days; day++) {
    cells.push(new Date(year, month, day))
  }
  while (cells.length % 7 !== 0) {
    cells.push(null)
  }
  const weeks: (Date | null)[][] = []
  for (let i = 0; i < cells.length; i += 7) {
    weeks.push(cells.slice(i, i + 7))
  }
  return weeks
}

/** 항목을 {@code at}이 가리키는 시각의 날짜별로 묶는다. 같은 날 안에서는 받은 순서를 지킨다. */
export function groupByDay<T>(items: T[], at: (item: T) => string): Map<string, T[]> {
  const groups = new Map<string, T[]>()
  for (const item of items) {
    const key = dateKey(new Date(at(item)))
    groups.set(key, [...(groups.get(key) ?? []), item])
  }
  return groups
}

/**
 * 오늘(또는 오늘 아직 안 했으면 어제)까지 하루도 빠지지 않고 연습한 날 수.
 * 오늘 연습을 아직 안 했다고 연속 기록이 끊긴 것처럼 보이지 않게 어제부터 셀 수 있게 한다.
 */
export function streak(practicedDays: Set<string>, today: Date): number {
  const cursor = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  if (!practicedDays.has(dateKey(cursor))) {
    cursor.setDate(cursor.getDate() - 1)
  }
  let count = 0
  while (practicedDays.has(dateKey(cursor))) {
    count++
    cursor.setDate(cursor.getDate() - 1)
  }
  return count
}

/** 연습한 시각: 마지막으로 답한 때, 아직 답이 없으면 시작한 때. */
export function activityAt(item: { createdAt: string; lastAnsweredAt: string | null }): string {
  return item.lastAnsweredAt ?? item.createdAt
}

export function addMonths(date: Date, amount: number): Date {
  return new Date(date.getFullYear(), date.getMonth() + amount, 1)
}
