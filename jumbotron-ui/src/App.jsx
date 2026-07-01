import './App.css'
import { MainPanel } from './components/MainPanel'
import { Sidebar } from './components/Sidebar'
import { TaskPanel } from './components/TaskPanel'
import { useAsk } from './hooks/useAsk'

export default function App() {
  const chat = useAsk()
  const masterPlayer = chat.masterPlayer

  return (
    <div className="app-layout">
      <Sidebar
        sessions={chat.sessions}
        activeId={chat.activeId}
        onSelect={chat.loadSession}
        onNew={chat.newSession}
        masterSelectPending={chat.masterSelectPending}
        onMasterSelect={chat.createSessionWithMaster}
        onCancelMasterSelect={chat.cancelMasterSelect}
      />
      <MainPanel
        activeSession={chat.activeSession}
        playerStatus={chat.playerStatus}
        isLoading={chat.isLoading}
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
        streaming={chat.streaming}
        tokens={chat.tokens}
        masterPlayer={masterPlayer}
        flowPhase={chat.flowPhase}
        onShowPlayerDetail={chat.showPlayerDetail}
      />
    </div>
  )
}
