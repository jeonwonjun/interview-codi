import { CalendarDays, LogOut, MessageSquareText, UserRound } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router'
import styles from './AppShell.module.css'
import { IconButton } from './IconButton'
import { NavTabs } from './NavTabs'

const DESTINATIONS = [
  { to: '/', label: '연습', icon: MessageSquareText, end: true },
  { to: '/practices', label: '기록', icon: CalendarDays, end: false },
  { to: '/me', label: '내 정보', icon: UserRound, end: true },
]

/**
 * 로그인한 뒤의 화면 틀. 폭에 따라 이동 방법이 다르다(DESIGN.md Layout).
 * - 웹(1024px 이상): 왼쪽 메뉴
 * - 태블릿(768~1023px): 위쪽 탭
 * - 앱(768px 미만): 아래쪽 탭. 로그아웃은 내 정보에서 한다.
 * 보이지 않는 메뉴는 display: none이라 화면 읽기 프로그램에도 하나만 읽힌다.
 */
export function AppShell({ onLogout, children }: { onLogout: () => void; children: ReactNode }) {
  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar}>
        <Brand />
        <nav aria-label="화면 이동" className={styles.sideNav}>
          {DESTINATIONS.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className={styles.sideLink}>
              <Icon size={20} aria-hidden="true" />
              {label}
            </NavLink>
          ))}
        </nav>
        <button type="button" className={styles.sideLogout} onClick={onLogout}>
          <LogOut size={18} aria-hidden="true" />
          로그아웃
        </button>
      </aside>

      <header className={styles.topbar}>
        <Brand />
        <div className={styles.topActions}>
          <NavTabs label="화면 이동" tabs={DESTINATIONS.map(({ to, label }) => ({ to, label }))} />
          <IconButton icon={LogOut} label="로그아웃" onClick={onLogout} />
        </div>
      </header>

      <main className={styles.main}>{children}</main>

      <nav aria-label="화면 이동" className={styles.bottomTabs}>
        {DESTINATIONS.map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end} className={styles.bottomTab}>
            <Icon size={22} aria-hidden="true" />
            {label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}

function Brand() {
  return (
    <Link to="/" className={styles.brand}>
      <img className={styles.logo} src="/favicon.svg" alt="" />
      면접코디
    </Link>
  )
}

/**
 * 한 화면 안의 본문과 오른쪽 칸. 오른쪽 칸은 웹(1024px 이상)에서만 보이고 스크롤해도 따라온다.
 * 좁은 화면에서는 본문만 보인다(오른쪽 칸 내용은 다른 탭에서 볼 수 있어야 한다).
 */
export function PageColumns({ aside, children }: { aside?: ReactNode; children: ReactNode }) {
  return (
    <div className={aside ? styles.columns : styles.single}>
      <div className={styles.primary}>{children}</div>
      {aside && <aside className={styles.aside}>{aside}</aside>}
    </div>
  )
}
