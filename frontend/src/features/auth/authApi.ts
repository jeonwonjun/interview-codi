import { api, ApiError } from '../../shared/api/client'

export type Me = {
  id: number
}

/** 카카오 로그인 시작 주소. 페이지 이동으로 연다(fetch로 호출하지 않는다). */
export const KAKAO_LOGIN_URL = '/oauth2/authorization/kakao'

/** 로그인하지 않았으면 null. */
export async function fetchMe(): Promise<Me | null> {
  try {
    return await api.get<Me>('/api/me')
  } catch (error) {
    if (error instanceof ApiError && error.isLoginRequired) {
      return null
    }
    throw error
  }
}

export function logout(): Promise<void> {
  return api.post<void>('/api/logout')
}
