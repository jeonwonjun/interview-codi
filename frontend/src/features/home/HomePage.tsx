import { Button } from '../../shared/ui/Button'
import styles from './HomePage.module.css'

/** 로그인 직후 화면. 두 연습 방법 중 하나를 고른다. 연습 기능은 아직 없다. */
export function HomePage() {
  return (
    <>
      <section className={styles.header}>
        <h1 className={styles.title}>어떻게 연습해 볼까요?</h1>
        <p className={styles.lead}>처음이라면 자료 없이 바로 시작해 보세요. 이력서는 나중에 더해도 돼요.</p>
      </section>

      <div className={styles.choices}>
        <section className={styles.choice}>
          <div className={styles.choiceHeader}>
            <h2 className={styles.choiceTitle}>자료 없이 바로 연습</h2>
            <span className={styles.badge}>준비 중</span>
          </div>
          <p className={styles.choiceBody}>내가 만든 프로젝트에 대한 질문으로 시작해요. 답변에 맞춰 꼬리질문이 이어져요.</p>
          <Button block disabled>
            연습 시작하기
          </Button>
        </section>

        <section className={styles.choice}>
          <div className={styles.choiceHeader}>
            <h2 className={styles.choiceTitle}>이력서로 맞춤 연습</h2>
            <span className={styles.badge}>준비 중</span>
          </div>
          <p className={styles.choiceBody}>이력서에 적은 경험을 바탕으로 질문을 만들어요.</p>
          <Button variant="secondary" block disabled>
            이력서 넣기
          </Button>
        </section>
      </div>
    </>
  )
}
