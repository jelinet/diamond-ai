import { MarkdownText } from './MarkdownText'

export function PlayerCard({ player, text, tokenInfo, status, isMaster }) {
  return (
    <div className="player-card">
      <div className="player-card-header" style={{ borderLeftColor: player.color }}>
        <span className="player-emoji">{player.emoji}</span>
        <span className="player-name" style={{ color: player.color }}>
          {player.label}{isMaster && <span className="master-crown">♛</span>}
        </span>
        {status === 'thinking' && <span className="badge thinking">thinking…</span>}
        {status === 'done' && tokenInfo && <span className="badge done">↑{tokenInfo.in} ↓{tokenInfo.out}</span>}
        {status === 'error' && <span className="badge error">error</span>}
        {status === 'stopped' && <span className="badge stopped">stopped</span>}
      </div>
      <div className="player-card-body">
        {text ? (
          <>
            <MarkdownText text={text} className="player-text" />
            {status === 'thinking' ? <span className="cursor" /> : null}
          </>
        ) : <p className="player-placeholder">Waiting for response…</p>}
      </div>
    </div>
  )
}
