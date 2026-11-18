import { KAKAO_LOGIN_URL } from './authApi'
import styles from './KakaoLoginButton.module.css'

/** 카카오 인가 화면으로 페이지를 이동한다. 로그인이 끝나면 백엔드가 "/"로 돌려보낸다. */
export function KakaoLoginButton() {
  return (
    <a className={styles.button} href={KAKAO_LOGIN_URL}>
      <svg className={styles.symbol} viewBox="0 0 18 18" aria-hidden="true">
        <path
          fill="#000"
          d="M9 1.5C4.58 1.5 1 4.32 1 7.8c0 2.24 1.49 4.2 3.73 5.32l-.95 3.47c-.08.3.26.54.52.37l4.15-2.74c.18.02.36.02.55.02 4.42 0 8-2.82 8-6.3S13.42 1.5 9 1.5z"
        />
      </svg>
      카카오 로그인
    </a>
  )
}
