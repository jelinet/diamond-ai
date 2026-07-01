import { useEffect, useRef, useState } from 'react'
import { PLAYERS } from '../constants/players'
import { PlayerCard } from './PlayerCard'
import { SynthesisCard } from './SynthesisCard'

export function TurnBlock({ turn, isCurrent, playerStatus, masterPlayer, detailRequest }) {
  const [expandedPlayer, setExpandedPlayer] = useState(null)
  const blockRef = useRef(null)
  const lastDetailRequestIdRef = useRef(null)
  const visiblePlayers = PLAYERS.filter(player =>
    turn.visiblePlayers?.includes(player.key) || turn.answers?.[player.key],
  )
  const hasSynthesis = !!turn.synthesis
  const singlePlayer = visiblePlayers.length === 1 ? visiblePlayers[0] : null
  const canMirrorSingleAnswer = singlePlayer && (!isCurrent || turn.currentPhase === 'answering' || turn.currentPhase === null)
  const singlePlayerAnswer = canMirrorSingleAnswer ? turn.answers?.[singlePlayer.key] : ''
  const finalText = hasSynthesis ? turn.synthesis : singlePlayerAnswer
  const isSynthesising = isCurrent && ['synthesis', 'assembling'].includes(turn.currentPhase)
  const isSinglePlayerStreaming = isCurrent && singlePlayer && playerStatus[singlePlayer.key] === 'thinking'
  const showSynthesisCard = !!finalText || isSynthesising || isSinglePlayerStreaming
  const intentType = typeof turn.intent === 'string' ? turn.intent : turn.intent?.intentType

  useEffect(() => {
    if (!isCurrent || !detailRequest?.player) return
    if (lastDetailRequestIdRef.current === detailRequest.id) return
    if (!visiblePlayers.some(player => player.key === detailRequest.player)) return
    lastDetailRequestIdRef.current = detailRequest.id
    setExpandedPlayer(current => current === detailRequest.player ? null : detailRequest.player)
    blockRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [detailRequest?.id, detailRequest?.player, isCurrent])

  return (
    <section className="turn-block" ref={blockRef}>
      <div className="user-question">
        {intentType && (
          <span className={`intent-badge intent-${intentType.toLowerCase()}`}>
            {intentType === 'ANALYZE' ? '分析' : intentType === 'EXECUTE' ? '执行' : ''}
          </span>
        )}
        {turn.question}
      </div>

      {showSynthesisCard && (
        <SynthesisCard
          text={finalText}
          isStreaming={isSynthesising || isSinglePlayerStreaming}
          masterPlayer={masterPlayer}
        />
      )}

      {visiblePlayers.length > 0 && (
        <div className="process-section">
          <div className="process-tabs">
            {visiblePlayers.map(player => {
              const status = isCurrent ? (playerStatus[player.key] || 'idle') : (turn.answers?.[player.key] ? 'done' : 'idle')
              const isExpanded = expandedPlayer === player.key

              return (
                <button
                  key={player.key}
                  className={`process-tab ${isExpanded ? 'active' : ''}`}
                  onClick={() => setExpandedPlayer(isExpanded ? null : player.key)}
                  style={{ borderColor: isExpanded ? player.color : undefined }}
                  aria-expanded={isExpanded}
                >
                  <span>{player.emoji}</span>
                  <span>{player.label}</span>
                  <span className={`process-tab-status st-${status}`}>{status}</span>
                  <span className="process-tab-toggle">{isExpanded ? '收起' : '展开'}</span>
                </button>
              )
            })}
          </div>
          {visiblePlayers.map(player => {
            const status = isCurrent ? (playerStatus[player.key] || 'idle') : (turn.answers?.[player.key] ? 'done' : 'idle')
            const isExpanded = expandedPlayer === player.key

            return isExpanded ? (
              <div className="process-card-wrap" key={player.key}>
                <PlayerCard
                  player={player}
                  text={turn.answers?.[player.key]}
                  tokenInfo={turn.tokens?.[player.key]}
                  status={status}
                  isMaster={player.key === masterPlayer}
                />
              </div>
            ) : null
          })}
        </div>
      )}
    </section>
  )
}
