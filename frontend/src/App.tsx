import { useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { MyPage } from './features/account/MyPage'
import { LoginPage } from './features/auth/LoginPage'
import { useAuth } from './features/auth/useAuth'
import { HistoryPage } from './features/history/HistoryPage'
import { HomePage } from './features/home/HomePage'
import { PracticePage } from './features/practice/PracticePage'
import { setUnauthorizedHandler } from './shared/api/client'
import { AppShell } from './shared/ui/AppShell'
import { ErrorBanner } from './shared/ui/ErrorBanner'
import { Layout } from './shared/ui/Layout'
import { ToastProvider } from './shared/ui/ToastProvider'

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
  const logoutAction = () => void handleLogout()
  return (
    <AppShell onLogout={logoutAction}>
      {logoutFailed && <ErrorBanner>로그아웃하지 못했어요. 다시 시도해 주세요.</ErrorBanner>}
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/practices" element={<HistoryPage />} />
        <Route path="/practices/:practiceId" element={<PracticePage />} />
        <Route path="/me" element={<MyPage onLogout={logoutAction} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppShell>
  )
}
