import { PLAYERS } from '../constants/players'

export function MasterSelectPanel({ onSelect, onCancel }) {
  return (
    <div className="master-select-panel">
      <div className="master-select-title">Choose Master</div>
      <div className="master-select-subtitle">The Master detects intent, coordinates work, and produces the final answer while also contributing directly.</div>
      <div className="master-select-options">
        {PLAYERS.map(player => (
          <button
            key={player.key}
            className="master-option-btn"
            onClick={() => onSelect(player.key)}
            style={{ borderColor: player.color }}
          >
            <span className="master-option-emoji">{player.emoji}</span>
            <span className="master-option-label" style={{ color: player.color }}>{player.label}</span>
          </button>
        ))}
      </div>
      <button className="master-select-cancel" onClick={onCancel}>Cancel</button>
    </div>
  )
}
