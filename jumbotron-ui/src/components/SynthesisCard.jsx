import { PLAYER_MAP } from '../constants/players'
import { MarkdownText } from './MarkdownText'

export function SynthesisCard({ text, isStreaming, masterPlayer }) {
  const master = PLAYER_MAP[masterPlayer]

  return (
    <div className="synthesis-card">
      <div className="synthesis-header">
        {master && <span className="synthesis-master-emoji">{master.emoji}</span>}
        <span className="synthesis-title">最终结论</span>
        {isStreaming && <span className="badge thinking">generating…</span>}
      </div>
      <div className="synthesis-body">
        {text ? (
          <>
            <MarkdownText text={text} className="synthesis-text" />
            {isStreaming ? <span className="cursor" /> : null}
          </>
        ) : <p className="player-placeholder">Generating…</p>}
      </div>
    </div>
  )
}
