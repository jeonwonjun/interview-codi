import { useState } from 'react'
import { LoginPage } from './features/auth/LoginPage'
import { useAuth } from './features/auth/useAuth'
import { HomePage } from './features/home/HomePage'
import { Button } from './shared/ui/Button'
import { ErrorBanner } from './shared/ui/ErrorBanner'
import { Layout } from './shared/ui/Layout'

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
  const { state, logout } = useAuth()
  const [loginFailed] = useState(consumeLoginError)
  const [logoutFailed, setLogoutFailed] = useState(false)

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
        <Button variant="ghost" onClick={handleLogout}>
          로그아웃
        </Button>
      }
    >
      {logoutFailed && <ErrorBanner>로그아웃하지 못했어요. 다시 시도해 주세요.</ErrorBanner>}
      <HomePage />
    </Layout>
  )
}
