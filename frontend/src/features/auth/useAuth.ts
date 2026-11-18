import { useCallback, useEffect, useState } from 'react'
import { fetchMe, logout as requestLogout, type Me } from './authApi'

type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; me: Me }
  | { status: 'error'; message: string }

async function loadAuthState(): Promise<AuthState> {
  try {
    const me = await fetchMe()
    return me ? { status: 'authenticated', me } : { status: 'anonymous' }
  } catch {
    return { status: 'error', message: '서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.' }
  }
}

/** 처음 화면을 열 때 서버 세션으로 로그인 여부를 확인한다. */
export function useAuth() {
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  useEffect(() => {
    let ignore = false
    loadAuthState().then((next) => {
      if (!ignore) {
        setState(next)
      }
    })
    return () => {
      ignore = true
    }
  }, [])

  const logout = useCallback(async () => {
    await requestLogout()
    setState({ status: 'anonymous' })
  }, [])

  /** API가 401을 받으면(세션 만료 등) 로그인 화면으로 돌아간다. */
  const markAnonymous = useCallback(() => setState({ status: 'anonymous' }), [])

  return { state, logout, markAnonymous }
}
