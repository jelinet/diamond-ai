import { PLAYER_MAP } from '../constants/players'
import { MarkdownText } from './MarkdownText'

export function SynthesisCard({ text, isStreaming, masterPlayer }) {
  const master = PLAYER_MAP[masterPlayer]

  return (
    <div className="synthesis-card">
      <div className="synthesis-header">
        {master && <span className="synthesis-master-emoji">{master.emoji}</span>}
        <span className="synthesis-title">Final Conclusion</span>
        {isStreaming && <span className="badge thinking">generating…</span>}
      </div>
      <div className="synthesis-body">
        {text ? (
          <MarkdownText text={text} className="synthesis-text" />
        ) : <p className="player-placeholder">Generating…</p>}
      </div>
    </div>
  )
}
