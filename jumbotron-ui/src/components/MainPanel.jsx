import { useEffect, useRef, useState } from 'react'
import { TurnBlock } from './TurnBlock'
import { DecompositionCard } from './DecompositionCard'
import { ClarificationCard } from './ClarificationCard'
import { MasterSelectPanel } from './MasterSelectPanel'
import { searchSessions } from '../services/chatApi'

function formatChatDate(createdAt) {
  if (!createdAt) return ''

  const date = new Date(createdAt)
  if (Number.isNaN(date.getTime())) return ''

  return `${date.getMonth() + 1}/${date.getDate()}`
}

function chatTitle(session) {
  return session?.title === 'New conversation' ? 'New chat' : session?.title
}

export function MainPanel({
  searchOpen,
  activeSession,
  playerStatus,
  isLoading,
  onSelectSession,
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
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState([])
  const [searchLoading, setSearchLoading] = useState(false)
  const isComposingRef = useRef(false)
  const searchRequestRef = useRef(0)
  const searchWasOpenRef = useRef(false)
  const turns = activeSession?.turns || []
  const hasContent = turns.length > 0
  const canInput = !isLoading && !awaitingConfirmation && !clarificationOptions && !pendingMasterQuestion

  const submit = () => {
    const question = input.trim()
    if (!question || !canInput) return
    setInput('')
    onAsk(question)
  }

  const runSearch = (query) => {
    const requestId = searchRequestRef.current + 1
    searchRequestRef.current = requestId
    setSearchLoading(true)
    return searchSessions(query, 10)
      .then(items => {
        if (searchRequestRef.current === requestId) {
          setSearchResults(Array.isArray(items) ? items : [])
        }
      })
      .catch(() => {
        if (searchRequestRef.current === requestId) {
          setSearchResults([])
        }
      })
      .finally(() => {
        if (searchRequestRef.current === requestId) {
          setSearchLoading(false)
        }
      })
  }

  useEffect(() => {
    if (!searchOpen) {
      searchWasOpenRef.current = false
      return
    }

    if (!searchWasOpenRef.current) {
      searchWasOpenRef.current = true
      if (searchQuery) {
        setSearchQuery('')
        return
      }
    }

    if (searchQuery) {
      runSearch(searchQuery.trim())
      return
    }

    runSearch('')
  }, [searchOpen, searchQuery])

  if (searchOpen) {
    return (
      <main className="main-panel">
        <div className="search-chats-view">
          <div className="search-chats-inner">
            <div className="search-input-wrap">
              <svg className="search-input-icon" viewBox="0 0 24 24" focusable="false" aria-hidden="true">
                <circle cx="11" cy="11" r="7" />
                <path d="M20 20l-3.8-3.8" />
              </svg>
              <input
                className="search-chats-input"
                type="search"
                placeholder="Search chats"
                value={searchQuery}
                onChange={event => setSearchQuery(event.target.value)}
                autoFocus
              />
              {searchQuery && (
                <button
                  className="search-clear-btn"
                  type="button"
                  aria-label="Clear search"
                  onClick={() => {
                    setSearchQuery('')
                  }}
                >
                  <svg className="search-clear-icon" viewBox="0 0 16 16" focusable="false" aria-hidden="true">
                    <path d="M4.5 4.5l7 7" />
                    <path d="M11.5 4.5l-7 7" />
                  </svg>
                </button>
              )}
            </div>

            <div className="search-results">
              {searchLoading ? (
                <div className="search-empty">Searching...</div>
              ) : searchResults.length === 0 ? (
                <div className="search-empty">No matching chats</div>
              ) : searchResults.map(session => {
                const lastQuestion = session.lastQuestion || [...(session.turns || [])].reverse().find(turn => turn.question)?.question
                const chatDate = formatChatDate(session.createdAt)
                return (
                  <button
                    key={session.id}
                    className="search-result-item"
                    type="button"
                    onClick={() => onSelectSession?.(session)}
                  >
                    <span className="search-result-content">
                      <span className="search-result-title">{chatTitle(session)}</span>
                      {lastQuestion && <span className="search-result-preview">{lastQuestion}</span>}
                    </span>
                    {chatDate && <span className="search-result-date">{chatDate}</span>}
                  </button>
                )
              })}
            </div>
          </div>
        </div>
      </main>
    )
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
          onCompositionStart={() => {
            isComposingRef.current = true
          }}
          onCompositionEnd={() => {
            isComposingRef.current = false
          }}
          onKeyDown={event => {
            const isComposing = isComposingRef.current || event.nativeEvent.isComposing || event.keyCode === 229
            if (isComposing) return

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
