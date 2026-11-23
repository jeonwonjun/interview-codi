import type { ReactNode } from 'react'
import { Link } from 'react-router'
import styles from './Layout.module.css'

/** 로그인 전·로딩 화면의 틀. 로그인한 뒤에는 AppShell을 쓴다. */
export function Layout({ children }: { children: ReactNode }) {
  return (
    <>
      <header>
        <div className={styles.nav}>
          <Link to="/" className={styles.brand}>
            <img className={styles.logo} src="/favicon.svg" alt="" />
            면접코디
          </Link>
        </div>
      </header>
      <main className={styles.main}>{children}</main>
    </>
  )
}
