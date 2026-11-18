import type { ReactNode } from 'react'
import styles from './Layout.module.css'

export function Layout({ actions, children }: { actions?: ReactNode; children: ReactNode }) {
  return (
    <>
      <header>
        <nav className={styles.nav}>
          <span className={styles.brand}>
            <img className={styles.logo} src="/favicon.svg" alt="" />
            면접코디
          </span>
          {actions}
        </nav>
      </header>
      <main className={styles.main}>{children}</main>
    </>
  )
}
