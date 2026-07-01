import { INTENT_BY_OPTION_INDEX } from '../constants/flow'

export function ClarificationCard({ options, onSelect }) {
  return (
    <div className="clarif-card">
      <div className="clarif-header">Master 需要您确认意图：</div>
      {options.map((option, index) => (
        <button key={index} className="clarif-option-btn" onClick={() => onSelect(INTENT_BY_OPTION_INDEX[index] || 'ANALYZE')}>
          {index === 0 ? '🔍 ' : '⚙️ '}{option}
        </button>
      ))}
    </div>
  )
}
