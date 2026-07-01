import { useState } from 'react'
import { TurnBlock } from './TurnBlock'
import { DecompositionCard } from './DecompositionCard'
import { ClarificationCard } from './ClarificationCard'
import { MasterSelectPanel } from './MasterSelectPanel'

export function MainPanel({
  activeSession,
  playerStatus,
  isLoading,
  onAsk,
  onCancel,
  pendingDecomposition,
  awaitingConfirmation,
  clarificationOptions,
  pendingMasterQuestion,
  detailRequest,
  onConfirmPlan,
  onCancelPlan,
  onSelectIntent,
  onMasterSelectForQuestion,
  onCancelMasterQuestion,
  masterPlayer,
}) {
  const [input, setInput] = useState('')
  const turns = activeSession?.turns || []
  const hasContent = turns.length > 0
  const canInput = !isLoading && !awaitingConfirmation && !clarificationOptions && !pendingMasterQuestion

  const submit = () => {
    const question = input.trim()
    if (!question || !canInput) return
    setInput('')
    onAsk(question)
  }

  return (
    <main className="main-panel">
      <div className="answers-area">
        {!hasContent && canInput && (
          <div className="welcome">
            <div className="welcome-icon">🏟️</div>
            <p className="welcome-title">Diamond</p>
            <p className="welcome-sub">Let's play a great game together!</p>
          </div>
        )}
        {turns.map((turn, index) => (
          <TurnBlock
            key={turn.id}
            turn={turn}
            isCurrent={index === turns.length - 1}
            playerStatus={playerStatus}
            masterPlayer={masterPlayer}
            detailRequest={detailRequest}
          />
        ))}
        {pendingMasterQuestion && (
          <div className="inline-master-select">
            <div className="pending-question-preview">{pendingMasterQuestion}</div>
            <MasterSelectPanel
              onSelect={onMasterSelectForQuestion}
              onCancel={onCancelMasterQuestion}
            />
          </div>
        )}
        {awaitingConfirmation && pendingDecomposition && (
          <DecompositionCard
            decomposition={pendingDecomposition}
            onConfirm={onConfirmPlan}
            onCancel={onCancelPlan}
          />
        )}
        {clarificationOptions && (
          <ClarificationCard options={clarificationOptions} onSelect={onSelectIntent} />
        )}
      </div>

      <div className="input-area">
        <textarea
          className="question-input"
          placeholder="Enter your question... (Enter to send, Shift+Enter for a new line)"
          value={input}
          onChange={event => setInput(event.target.value)}
          onKeyDown={event => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault()
              submit()
            }
          }}
          rows={3}
          disabled={!canInput}
        />
        {isLoading && <button className="stop-btn" onClick={onCancel}>■ Stop</button>}
        <button className="send-btn" onClick={submit} disabled={!canInput || !input.trim()}>
          Send ↵
        </button>
      </div>
    </main>
  )
}
