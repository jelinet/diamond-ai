import { PHASE_PANEL_LABEL, STATUS_LABEL } from '../constants/flow'
import { PLAYERS } from '../constants/players'

export function TaskPanel({ playerStatus, playerTasks, streaming, tokens, masterPlayer, flowPhase, onShowPlayerDetail }) {
  const phaseLabel = PHASE_PANEL_LABEL[flowPhase] || ''

  return (
    <aside className="task-panel">
      <div className="task-panel-header">
        <span>Player Status</span>
        <span className="task-panel-meta">
          {phaseLabel && <span className="task-panel-phase">{phaseLabel}</span>}
        </span>
      </div>

      {PLAYERS.map(player => {
        const status = playerStatus[player.key] || 'idle'
        const tokenInfo = tokens[player.key]
        const task = playerTasks[player.key]
        const wordCount = streaming[player.key]?.trim().split(/\s+/).filter(Boolean).length || 0
        const hasDetail = !!streaming[player.key]
        const isMaster = player.key === masterPlayer

        return (
          <div
            key={player.key}
            className={`task-card status-${status} ${isMaster ? 'master' : ''}`}
            style={isMaster ? { '--master-color': player.color } : undefined}
          >
            <div
              className="task-card-top"
              onClick={() => hasDetail && onShowPlayerDetail?.(player.key)}
              style={{ cursor: hasDetail ? 'pointer' : 'default' }}
            >
              <span className="task-dot" style={{
                background: status === 'idle' ? '#d1d5db' : status === 'thinking' ? player.color : status === 'done' ? '#22c55e' : '#ef4444',
              }} />
              <span className="task-player-identity">
                <span className="task-player-name" style={{ color: player.color }}>
                  {player.emoji} {player.role}
                </span>
                <span className="task-player-llm">LLM · {player.llm}</span>
              </span>
              {isMaster && <span className="task-master-badge">MASTER</span>}
              <span className={`task-status-label st-${status}`}>{STATUS_LABEL[status] ?? status}</span>
            </div>

            {task && <div className="task-description">{task}</div>}

            <div className="task-detail">
              {status === 'thinking' && (
                <div className="task-progress">
                  <div className="progress-bar"><div className="progress-fill" style={{ background: player.color }} /></div>
                  <span>{wordCount} words</span>
                </div>
              )}
              {status !== 'thinking' && tokenInfo && (
                <div className="task-tokens">
                  <div className="token-row"><span>Input</span><strong>{tokenInfo.in}</strong></div>
                  <div className="token-row"><span>Output</span><strong>{tokenInfo.out}</strong></div>
                  <div className="token-row total"><span>Total</span><strong>{tokenInfo.in + tokenInfo.out}</strong></div>
                </div>
              )}
              {status === 'error' && <span className="task-error-msg">Failed to respond</span>}
              {status === 'stopped' && <span className="task-stopped-msg">Cancelled</span>}
              {status === 'idle' && !task && !tokenInfo && <span className="task-idle-msg">Ready</span>}
            </div>
          </div>
        )
      })}
    </aside>
  )
}
