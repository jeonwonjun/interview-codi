import { describe, expect, it } from 'vitest'
import { phrasesOf, revealTextDuration } from './revealText'

describe('revealText', () => {
  it('어절 두 개씩 묶는다', () => {
    expect(phrasesOf('기술을 고른 이유를 먼저  말해서')).toEqual(['기술을 고른', '이유를 먼저', '말해서'])
  })

  it('글이 길어도 1.2초 안에 다 나타난다', () => {
    const long = Array.from({ length: 200 }, (_, index) => `어절${index}`).join(' ')

    expect(revealTextDuration('짧은 문장')).toBe(45)
    expect(revealTextDuration(long)).toBe(1_200)
  })
})
