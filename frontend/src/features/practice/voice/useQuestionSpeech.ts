import { useCallback, useEffect, useState, useSyncExternalStore } from 'react'

const STORAGE_KEY = 'interview-codi.readQuestionAloud'

function koreanVoice(): SpeechSynthesisVoice | undefined {
  return window.speechSynthesis.getVoices().find((voice) => voice.lang.toLowerCase().startsWith('ko'))
}

const supported = typeof window !== 'undefined' && 'speechSynthesis' in window

/**
 * 목소리 목록은 늦게 채워진다(Chrome). 채워졌다는 voiceschanged를 화면이 뜨기 전에 놓치는 경우가 있어,
 * 이벤트와 함께 처음 몇 초는 직접 다시 확인한다.
 */
function subscribeVoices(onChange: () => void): () => void {
  if (!supported) {
    return () => undefined
  }
  window.speechSynthesis.addEventListener('voiceschanged', onChange)
  const timers = [300, 1_000, 3_000].map((delay) => setTimeout(onChange, delay))
  return () => {
    window.speechSynthesis.removeEventListener('voiceschanged', onChange)
    timers.forEach(clearTimeout)
  }
}

function hasKoreanVoice(): boolean {
  return supported && koreanVoice() != null
}

function readPreference(): boolean {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === 'on'
  } catch {
    return false
  }
}

function writePreference(enabled: boolean) {
  try {
    window.localStorage.setItem(STORAGE_KEY, enabled ? 'on' : 'off')
  } catch {
    // 저장하지 못하면 이번 화면에서만 기억한다.
  }
}

/**
 * 질문을 브라우저 내장 음성으로 읽어 준다(설계 1절, 결정: 브라우저 음성). 서버로 보내는 것이 없다.
 * 켜 두면 새 질문이 나올 때마다 읽는다. 한국어 목소리가 없는 기기에서는 {@code available}이 false라 버튼을 숨긴다.
 * 브라우저는 사용자가 화면을 누르기 전에는 소리를 내지 못하게 하므로, 처음에는 꺼 둔다.
 */
export function useQuestionSpeech(text: string) {
  const available = useSyncExternalStore(subscribeVoices, hasKoreanVoice, () => false)
  const [enabled, setEnabled] = useState(readPreference)

  const stop = useCallback(() => {
    if (supported) {
      window.speechSynthesis.cancel()
    }
  }, [])

  const speak = useCallback(
    (content: string) => {
      const voice = supported ? koreanVoice() : undefined
      if (!voice) {
        return
      }
      window.speechSynthesis.cancel()
      const utterance = new SpeechSynthesisUtterance(content)
      utterance.voice = voice
      utterance.lang = voice.lang
      window.speechSynthesis.speak(utterance)
    },
    [],
  )

  useEffect(() => {
    if (enabled && available) {
      speak(text)
    }
    return stop
  }, [enabled, available, text, speak, stop])

  const toggle = useCallback(() => {
    setEnabled((previous) => {
      writePreference(!previous)
      return !previous
    })
  }, [])

  return { available, enabled, toggle, stop }
}
