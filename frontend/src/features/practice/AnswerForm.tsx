import { useState } from 'react'
import { Button } from '../../shared/ui/Button'
import { Card } from '../../shared/ui/Card'
import { TextArea } from '../../shared/ui/TextArea'
import styles from './Practice.module.css'

const MAX_LENGTH = 3_000
// 말하는 속도 약 분당 300~500자. 안내일 뿐 막지 않는다.
const ONE_MINUTE_MIN = 300
const ONE_MINUTE_MAX = 500

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
  const inOneMinute = length >= ONE_MINUTE_MIN && length <= ONE_MINUTE_MAX

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
          hint={lengthHint(length)}
          hintTone={inOneMinute ? 'positive' : 'default'}
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

function lengthHint(length: number): string {
  if (length === 0) {
    return '1분 정도면 300~500자예요'
  }
  if (length < ONE_MINUTE_MIN) {
    return `${length}자 · 1분 분량은 300~500자`
  }
  if (length <= ONE_MINUTE_MAX) {
    return `${length}자 · 1분 분량이에요`
  }
  return `${length}자 · 1분보다 조금 길어요`
}
