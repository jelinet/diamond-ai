import { PLAYER_MAP } from '../constants/players'

export function DecompositionCard({ decomposition, onConfirm, onCancel }) {
  return (
    <div className="decomp-card">
      <div className="decomp-header">Master 已完成任务拆分，请确认分工后开始执行：</div>
      {decomposition.subtasks.map(subtask => {
        const player = PLAYER_MAP[subtask.player]
        return (
          <div key={subtask.player} className="decomp-item">
            <span className="decomp-player" style={{ color: player?.color }}>{player?.emoji} {subtask.player}</span>
            <div className="decomp-detail">
              <div className="decomp-task">{subtask.taskDescription}</div>
              <div className="decomp-deliver">产出：{subtask.deliverable}</div>
            </div>
          </div>
        )
      })}
      <div className="decomp-actions">
        <button className="decomp-confirm-btn" onClick={onConfirm}>Confirmed; commencing execution. ▶</button>
        <button className="decomp-cancel-btn" onClick={onCancel}>Cancel</button>
      </div>
    </div>
  )
}
