import { useState } from 'react'
import { Button } from '../../shared/ui/Button'
import { Card } from '../../shared/ui/Card'
import { TextArea } from '../../shared/ui/TextArea'
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
    <Card as="div">
      <form className={styles.form} onSubmit={handleSubmit}>
        <TextArea
          id="answer"
          label="내 답변"
          value={content}
          maxLength={MAX_LENGTH}
          placeholder="말하듯이 적어 보세요. 1분 정도면 300~500자예요."
          hint={`${length}자 · 약 1분 분량은 300~500자`}
          onChange={(event) => setContent(event.target.value)}
        />
        <div className={styles.formActions}>
          {onCancel && (
            <Button variant="ghost" onClick={onCancel} disabled={busy}>
              취소
            </Button>
          )}
          <Button type="submit" loading={busy} disabled={length === 0}>
            {submitLabel}
          </Button>
        </div>
      </form>
    </Card>
  )
}
