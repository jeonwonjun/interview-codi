import type { Feedback } from './practiceApi'

export const FOCUS_LABEL: Record<NonNullable<Feedback['suggestionFocus']>, string> = {
  ACCURACY: '기술 설명',
  ANSWER: '질문에 대한 답',
  EVIDENCE: '근거',
  ORDER: '전달 순서',
}

export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso))
}
