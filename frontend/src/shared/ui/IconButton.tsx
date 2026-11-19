import type { LucideIcon } from 'lucide-react'
import type { ComponentProps } from 'react'
import styles from './IconButton.module.css'

type IconButtonProps = Omit<ComponentProps<'button'>, 'children' | 'aria-label'> & {
  icon: LucideIcon
  /** 화면 읽기 프로그램이 읽고, 마우스를 올리면 말풍선으로 보여 준다. 무엇을 하는지 동사로 쓴다. */
  label: string
  tone?: 'default' | 'danger'
}

/**
 * 글자 없이 아이콘만 둔 버튼. 닫기·삭제·펼치기처럼 아이콘만으로 뜻이 분명할 때만 쓴다(DESIGN.md Icons).
 * 누를 수 있는 크기는 40px을 지킨다.
 */
export function IconButton({ icon: Icon, label, tone = 'default', className, type = 'button', ...props }: IconButtonProps) {
  return (
    <button
      type={type}
      className={[styles.button, styles[tone], className].filter(Boolean).join(' ')}
      aria-label={label}
      title={label}
      {...props}
    >
      <Icon size={18} strokeWidth={2} aria-hidden="true" />
    </button>
  )
}
