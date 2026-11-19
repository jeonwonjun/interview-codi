import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { NavTabs } from './NavTabs'

const TABS = [
  { to: '/', label: '홈' },
  { to: '/practices', label: '기록' },
]

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <NavTabs label="화면 이동" tabs={TABS} />
    </MemoryRouter>,
  )
}

describe('NavTabs', () => {
  it('지금 주소의 탭만 선택된 탭으로 알린다', () => {
    renderAt('/practices')

    expect(screen.getByRole('link', { name: '기록' }).getAttribute('aria-current')).toBe('page')
    expect(screen.getByRole('link', { name: '홈' }).getAttribute('aria-current')).toBeNull()
  })

  it('누른 탭으로 선택이 옮겨 간다', async () => {
    renderAt('/')

    await userEvent.click(screen.getByRole('link', { name: '기록' }))

    expect(screen.getByRole('link', { name: '기록' }).getAttribute('aria-current')).toBe('page')
    expect(screen.getByRole('link', { name: '홈' }).getAttribute('aria-current')).toBeNull()
  })

  it('연습 상세처럼 맞는 탭이 없으면 아무 탭도 고르지 않는다', () => {
    renderAt('/practices/1')

    expect(screen.queryByRole('link', { current: 'page' })).toBeNull()
  })
})
