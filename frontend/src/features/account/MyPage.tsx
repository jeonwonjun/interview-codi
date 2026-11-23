import { FileText, LogOut } from 'lucide-react'
import { Button } from '../../shared/ui/Button'
import { Card } from '../../shared/ui/Card'
import styles from './MyPage.module.css'

/** 내 정보. 앱에서는 아래 탭에 로그아웃 자리가 없어서 여기서 한다. 이력서 관리는 이력서 기능과 함께 채운다. */
export function MyPage({ onLogout }: { onLogout: () => void }) {
  return (
    <div className={styles.page}>
      <h1 className={styles.title}>내 정보</h1>
      <Card>
        <h2 className={styles.heading}>
          <FileText size={20} aria-hidden="true" />
          내 이력서
        </h2>
        <p className={styles.body}>이력서로 맞춤 연습은 준비 중이에요. 준비되면 여기서 넣고 지울 수 있어요.</p>
      </Card>
      <Card>
        <h2 className={styles.heading}>계정</h2>
        <p className={styles.body}>카카오 회원번호만 저장하고, 이름·이메일·프로필은 받지 않아요.</p>
        <div>
          <Button variant="secondary" icon={LogOut} onClick={onLogout}>
            로그아웃
          </Button>
        </div>
      </Card>
    </div>
  )
}
