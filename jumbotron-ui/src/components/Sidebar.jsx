import { useState } from 'react'
import { MasterSelectPanel } from './MasterSelectPanel'

function getSessionTime(session) {
  const value = session?.createdAt
  if (!value) return Date.now()

  if (typeof value === 'number') return value

  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? Date.now() : parsed
}

function getThreeMonthCutoff() {
  const cutoff = new Date()
  cutoff.setMonth(cutoff.getMonth() - 3)
  return cutoff.getTime()
}

function isEmptyNewChat(session) {
  return ['New chat', 'New conversation'].includes(session.title) && (session.turns?.length || 0) === 0
}

function chatTitle(session) {
  return session?.title === 'New conversation' ? 'New chat' : session?.title
}

export function Sidebar({ sessions, activeId, onSelect, onNew, onSearch, masterSelectPending, onMasterSelect, onCancelMasterSelect }) {
  const [recentsOpen, setRecentsOpen] = useState(true)
  const threeMonthCutoff = getThreeMonthCutoff()
  const visibleSessions = sessions.filter(session =>
    getSessionTime(session) >= threeMonthCutoff &&
    !isEmptyNewChat(session)
  )

  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <div className="sidebar-brand">
          <span className="sidebar-brand-icon" aria-hidden="true">🏟️</span>
          <span className="sidebar-brand-text">Diamond</span>
        </div>
        <button className="sidebar-menu-btn" onClick={onNew}>
          <span className="sidebar-menu-icon" aria-hidden="true">
            <svg className="sidebar-line-icon" viewBox="0 0 24 24" focusable="false">
              <path d="M12 3H6a3 3 0 0 0-3 3v12a3 3 0 0 0 3 3h12a3 3 0 0 0 3-3v-6" />
              <path d="M18.4 2.6a1.5 1.5 0 0 1 2.1 2.1l-8.7 8.7-3.3 1 1-3.3 8.9-8.5z" />
            </svg>
          </span>
          <span>New chat</span>
        </button>
        {masterSelectPending && (
          <div className="sidebar-master-select">
            <MasterSelectPanel onSelect={onMasterSelect} onCancel={onCancelMasterSelect} />
          </div>
        )}
        <button className="sidebar-menu-btn" type="button" onClick={onSearch}>
          <span className="sidebar-menu-icon" aria-hidden="true">
            <svg className="sidebar-line-icon" viewBox="0 0 24 24" focusable="false">
              <circle cx="11" cy="11" r="7" />
              <path d="M20 20l-3.8-3.8" />
            </svg>
          </span>
          <span>Search chats</span>
        </button>
      </div>
      <div className="session-history">
        <button
          className="recents-toggle"
          type="button"
          aria-expanded={recentsOpen}
          onClick={() => setRecentsOpen(open => !open)}
        >
          <span>Recents</span>
          <span className={`disclosure-icon ${recentsOpen ? 'open' : ''}`} aria-hidden="true">
            <svg className="disclosure-chevron" viewBox="0 0 16 16" focusable="false">
              <path d="M6 4l4 4-4 4" />
            </svg>
          </span>
        </button>
        {recentsOpen && (
          <ul className="session-list">
            {visibleSessions.length === 0 && !masterSelectPending && (
              <li className="session-empty">No chats yet</li>
            )}
            {[...visibleSessions]
              .sort((a, b) => getSessionTime(b) - getSessionTime(a))
              .map(session => (
              <li
                key={session.id}
                className={`session-item ${session.id === activeId ? 'active' : ''}`}
                onClick={() => onSelect(session)}
              >
                <div className="session-item-top">
                  <span className="session-q">{chatTitle(session)}</span>
                </div>
              </li>
              ))}
          </ul>
        )}
      </div>
    </aside>
  )
}
