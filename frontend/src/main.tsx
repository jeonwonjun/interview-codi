import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css'
import './styles/fonts.css'
import './styles/tokens.css'
import './styles/global.css'
import { LucideProvider } from 'lucide-react'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* 아이콘 크기와 상관없이 화면에 그려지는 선을 1.75px로 맞춘다(DESIGN.md Icons). */}
    <LucideProvider strokeWidth={1.75} absoluteStrokeWidth>
      <App />
    </LucideProvider>
  </StrictMode>,
)
