import { useLayoutEffect, useRef, useState } from 'react'
import { matchPath, NavLink, useLocation } from 'react-router'
import styles from './NavTabs.module.css'

type Tab = { to: string; label: string }

type Indicator = { x: number; width: number; visible: boolean } | null

/**
 * 상단 화면 이동 탭. 선택된 탭 뒤의 알약이 다음 탭으로 미끄러져 간다(DESIGN.md Motion).
 * 주소가 어느 탭과도 정확히 맞지 않으면(연습 상세 등) 알약을 숨긴다.
 */
export function NavTabs({ tabs, label }: { tabs: Tab[]; label: string }) {
  const { pathname } = useLocation()
  const activeIndex = tabs.findIndex((tab) => matchPath({ path: tab.to, end: true }, pathname))
  const listRef = useRef<HTMLDivElement>(null)
  const [indicator, setIndicator] = useState<Indicator>(null)
  // 처음 자리를 잡을 때는 미끄러지지 않고 바로 그 자리에 둔다.
  const [placed, setPlaced] = useState(false)

  useLayoutEffect(() => {
    const list = listRef.current
    if (!list) {
      return
    }
    const measure = () => {
      const active = activeIndex >= 0 ? (list.children[activeIndex + 1] as HTMLElement | undefined) : undefined
      // 맞는 탭이 없으면 마지막 자리에서 흐려지기만 한다(왼쪽 끝으로 미끄러지지 않게).
      setIndicator((previous) =>
        active
          ? { x: active.offsetLeft, width: active.offsetWidth, visible: true }
          : previous && { ...previous, visible: false },
      )
    }
    measure()
    // 글꼴을 받은 뒤 탭 폭이 바뀌면 알약도 다시 맞춘다.
    if (typeof ResizeObserver === 'undefined') {
      return
    }
    const observer = new ResizeObserver(measure)
    observer.observe(list)
    return () => observer.disconnect()
  }, [activeIndex])

  useLayoutEffect(() => {
    if (indicator?.visible && !placed) {
      const frame = requestAnimationFrame(() => setPlaced(true))
      return () => cancelAnimationFrame(frame)
    }
  }, [indicator, placed])

  return (
    <nav aria-label={label}>
      <div ref={listRef} className={styles.list}>
        <span
          aria-hidden="true"
          className={[styles.indicator, placed && styles.placed].filter(Boolean).join(' ')}
          style={
            indicator
              ? { transform: `translateX(${indicator.x}px)`, width: indicator.width, opacity: indicator.visible ? 1 : 0 }
              : { opacity: 0 }
          }
        />
        {tabs.map((tab) => (
          <NavLink key={tab.to} to={tab.to} end className={styles.tab}>
            {tab.label}
          </NavLink>
        ))}
      </div>
    </nav>
  )
}
