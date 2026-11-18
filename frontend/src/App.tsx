import { useEffect, useState } from 'react'
import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router'
import { LoginPage } from './features/auth/LoginPage'
import { useAuth } from './features/auth/useAuth'
import { HistoryPage } from './features/history/HistoryPage'
import { HomePage } from './features/home/HomePage'
import { PracticePage } from './features/practice/PracticePage'
import { setUnauthorizedHandler } from './shared/api/client'
import { Button } from './shared/ui/Button'
import { ErrorBanner } from './shared/ui/ErrorBanner'
import { Layout } from './shared/ui/Layout'
import { ToastProvider } from './shared/ui/ToastProvider'
import layoutStyles from './shared/ui/Layout.module.css'

/** 백엔드가 로그인 실패 시 "/?login=error"로 돌려보낸다. 한 번 보여 준 뒤 주소에서 지운다. */
function consumeLoginError(): boolean {
  const url = new URL(window.location.href)
  if (url.searchParams.get('login') !== 'error') {
    return false
  }
  url.searchParams.delete('login')
  window.history.replaceState(null, '', url.pathname + url.search + url.hash)
  return true
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthenticatedApp />
      </ToastProvider>
    </BrowserRouter>
  )
}

function AuthenticatedApp() {
  const { state, logout, markAnonymous } = useAuth()
  const [loginFailed] = useState(consumeLoginError)
  const [logoutFailed, setLogoutFailed] = useState(false)

  useEffect(() => {
    setUnauthorizedHandler(markAnonymous)
    return () => setUnauthorizedHandler(undefined)
  }, [markAnonymous])

  const handleLogout = async () => {
    try {
      setLogoutFailed(false)
      await logout()
    } catch {
      setLogoutFailed(true)
    }
  }

  if (state.status === 'loading') {
    return <Layout>{null}</Layout>
  }
  if (state.status === 'error') {
    return (
      <Layout>
        <div style={{ paddingTop: 'var(--space-2xl)' }}>
          <ErrorBanner>{state.message}</ErrorBanner>
        </div>
      </Layout>
    )
  }
  if (state.status === 'anonymous') {
    return (
      <Layout>
        <LoginPage loginFailed={loginFailed} />
      </Layout>
    )
  }
  return (
    <Layout
      actions={
        <div className={layoutStyles.actions}>
          <Link to="/practices" className={layoutStyles.navLink}>
            기록
          </Link>
          <Button variant="ghost" onClick={() => void handleLogout()}>
            로그아웃
          </Button>
        </div>
      }
    >
      {logoutFailed && <ErrorBanner>로그아웃하지 못했어요. 다시 시도해 주세요.</ErrorBanner>}
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/practices" element={<HistoryPage />} />
        <Route path="/practices/:practiceId" element={<PracticePage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  )
}
