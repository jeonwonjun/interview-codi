import { ErrorBanner } from '../../shared/ui/ErrorBanner'
import { KakaoLoginButton } from './KakaoLoginButton'
import styles from './LoginPage.module.css'

const STEPS = [
  '내 프로젝트 경험에 대한 질문에 1분 정도로 답해요.',
  '질문에 답했는지, 근거와 순서가 괜찮은지 피드백을 받아요.',
  '바꿔 볼 한 가지를 골라 다시 답해 봐요.',
]

export function LoginPage({ loginFailed }: { loginFailed: boolean }) {
  return (
    <>
      <section className={styles.hero}>
        <span className={styles.eyebrow}>주니어 개발자 면접 연습</span>
        <h1 className={styles.title}>
          내가 만든 프로젝트,
          <br />
          말로 설명하는 연습부터.
        </h1>
        <p className={styles.lead}>이력서가 없어도 괜찮아요. 프로젝트 경험 질문에 답하면서 바로 시작할 수 있어요.</p>
      </section>

      <section className={styles.card} aria-label="로그인">
        <ol className={styles.steps}>
          {STEPS.map((step, index) => (
            <li key={step} className={styles.step}>
              <span className={styles.stepNumber}>{index + 1}</span>
              <span>{step}</span>
            </li>
          ))}
        </ol>
        {loginFailed && <ErrorBanner>로그인을 마치지 못했어요. 다시 시도해 주세요.</ErrorBanner>}
        <KakaoLoginButton />
        <p className={styles.note}>카카오 회원번호만 저장하고, 이름·이메일·프로필은 받지 않아요.</p>
      </section>
    </>
  )
}
