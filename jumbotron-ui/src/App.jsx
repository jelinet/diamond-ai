import './App.css'
import { MainPanel } from './components/MainPanel'
import { Sidebar } from './components/Sidebar'
import { TaskPanel } from './components/TaskPanel'
import { useAsk } from './hooks/useAsk'
import { useState } from 'react'

export default function App() {
  const chat = useAsk()
  const masterPlayer = chat.masterPlayer
  const [isSearchOpen, setIsSearchOpen] = useState(false)

  const loadSession = (session) => {
    setIsSearchOpen(false)
    chat.loadSession(session)
  }

  const newSession = () => {
    setIsSearchOpen(false)
    chat.newSession()
  }

  return (
    <div className="app-layout">
      <Sidebar
        sessions={chat.sessions}
        activeId={chat.activeId}
        onSelect={loadSession}
        onNew={newSession}
        onSearch={() => setIsSearchOpen(true)}
        masterSelectPending={chat.masterSelectPending}
        onMasterSelect={chat.createSessionWithMaster}
        onCancelMasterSelect={chat.cancelMasterSelect}
      />
      <MainPanel
        searchOpen={isSearchOpen}
        sessions={chat.sessions}
        activeSession={chat.activeSession}
        playerStatus={chat.playerStatus}
        isLoading={chat.isLoading}
        onSelectSession={loadSession}
        onAsk={chat.ask}
        onCancel={chat.cancel}
        pendingDecomposition={chat.pendingDecomposition}
        awaitingConfirmation={chat.awaitingConfirmation}
        clarificationOptions={chat.clarificationOptions}
        pendingMasterQuestion={chat.pendingMasterQuestion}
        detailRequest={chat.detailRequest}
        onConfirmPlan={chat.confirmPlan}
        onCancelPlan={chat.cancelPlan}
        onSelectIntent={chat.selectIntent}
        onMasterSelectForQuestion={chat.createSessionForPendingQuestion}
        onCancelMasterQuestion={chat.cancelPendingMasterQuestion}
        masterPlayer={masterPlayer}
      />
      <TaskPanel
        playerStatus={chat.playerStatus}
        playerTasks={chat.playerTasks}
        playerErrors={chat.playerErrors}
        streaming={chat.streaming}
        tokens={chat.tokens}
        masterPlayer={masterPlayer}
        flowPhase={chat.flowPhase}
        supervisor={chat.supervisor}
        onShowPlayerDetail={chat.showPlayerDetail}
      />
    </div>
  )
}
