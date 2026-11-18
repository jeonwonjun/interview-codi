import { useState } from 'react'
import { Button } from '../../shared/ui/Button'
import styles from './Practice.module.css'

const MAX_LENGTH = 3_000

type Props = {
  initialValue?: string
  submitLabel: string
  busy: boolean
  onSubmit: (content: string) => Promise<boolean>
  onCancel?: () => void
}

/** 텍스트 답변 입력. 약 1분 분량은 안내일 뿐 막지 않는다. 제출에 실패하면 입력한 내용을 지우지 않는다. */
export function AnswerForm({ initialValue = '', submitLabel, busy, onSubmit, onCancel }: Props) {
  const [content, setContent] = useState(initialValue)
  const length = [...content.trim()].length

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    const succeeded = await onSubmit(content)
    if (succeeded && !initialValue) {
      setContent('')
    }
  }

  return (
    <form className={styles.card} onSubmit={handleSubmit}>
      <label className={styles.label} htmlFor="answer">
        내 답변
      </label>
      <textarea
        id="answer"
        className={styles.textarea}
        value={content}
        maxLength={MAX_LENGTH}
        placeholder="말하듯이 적어 보세요. 1분 정도면 300~500자예요."
        onChange={(event) => setContent(event.target.value)}
      />
      <div className={styles.formFooter}>
        <span className={styles.caption}>
          {length}자 · 약 1분 분량은 300~500자
        </span>
        <div className={styles.actions}>
          {onCancel && (
            <Button variant="ghost" onClick={onCancel} disabled={busy}>
              취소
            </Button>
          )}
          <Button type="submit" disabled={busy || length === 0}>
            {submitLabel}
          </Button>
        </div>
      </div>
    </form>
  )
}
