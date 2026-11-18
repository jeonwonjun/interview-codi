import { type TextareaHTMLAttributes, useId } from 'react'
import styles from './TextArea.module.css'

type TextAreaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label: string
  /** 아래에 보여 줄 안내(글자 수 등). */
  hint?: string
  /** 오류 문구. 있으면 테두리가 바뀌고 화면 읽기 프로그램에 함께 읽힌다. */
  error?: string
}

/** 답변처럼 긴 글을 쓰는 입력란. 포커스는 primary-ink 테두리, 오류는 negative 테두리와 문구로 알린다. */
export function TextArea({ label, hint, error, id, className, ...props }: TextAreaProps) {
  const generated = useId()
  const inputId = id ?? generated
  const hintId = `${inputId}-hint`
  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={inputId}>
        {label}
      </label>
      <textarea
        id={inputId}
        className={[styles.textarea, error && styles.invalid, className].filter(Boolean).join(' ')}
        aria-invalid={error ? true : undefined}
        aria-describedby={hint || error ? hintId : undefined}
        {...props}
      />
      {(error || hint) && (
        <span id={hintId} className={error ? styles.error : styles.hint}>
          {error ?? hint}
        </span>
      )}
    </div>
  )
}
