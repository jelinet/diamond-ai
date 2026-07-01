import { useEffect, useRef, useState } from 'react'
import { PLAYERS, PLAYER_KEYS } from '../constants/players'
import {
  EVENT,
  MASTER_WORK_PHASES,
  PHASE_TASK_LABEL,
  PHASES,
  PLAYER_WORK_PHASES,
  STATUS,
} from '../constants/flow'
import {
  askStream,
  cancelConversation,
  cancelPendingPlan,
  createSession,
  getSession,
  listSessions,
  saveSession,
} from '../services/chatApi'
import { createSseParser } from '../utils/sse'

function createEmptyStatus(status = STATUS.THINKING) {
  return Object.fromEntries(PLAYER_KEYS.map(key => [key, status]))
}

function createEmptyTasks() {
  return Object.fromEntries(PLAYER_KEYS.map(key => [key, '']))
}

function phaseOutputKey(phase) {
  if (phase === PHASES.ROUND_2) return 'round2'
  if (phase === PHASES.EXECUTING) return 'exec'
  return 'round1'
}

function phaseTaskLabel(phase) {
  return PHASE_TASK_LABEL[phase] || ''
}

function playersForIntent(intentType, masterPlayer) {
  switch (intentType) {
    case 'CHAT':
    case 'UNKNOWN':
      return [masterPlayer || 'PITCHER']
    case 'SEARCH':
      return ['FIELDER']
    case 'CODE':
      return ['PITCHER']
    case 'DATA':
    case 'WRITE':
      return PLAYER_KEYS
    case 'TASK_PLAN':
      return [masterPlayer || 'PITCHER']
    default:
      return []
  }
}

function createTurn(id, question) {
  return {
    id,
    question,
    synthesis: '',
    answers: {},
    phaseOutputs: { round1: {}, round2: {}, exec: {} },
    tokens: {},
    intent: null,
    currentPhase: null,
    subtasks: null,
    visiblePlayers: [],
  }
}

function normalizeTurn(turn) {
  return {
    ...createTurn(turn?.id || `${Date.now()}`, turn?.question || ''),
    ...turn,
    answers: turn?.answers || {},
    phaseOutputs: turn?.phaseOutputs || { round1: {}, round2: {}, exec: {} },
    tokens: turn?.tokens || {},
    currentPhase: turn?.currentPhase || null,
    subtasks: turn?.subtasks || null,
    visiblePlayers: turn?.visiblePlayers || Object.keys(turn?.answers || {}),
  }
}

function normalizeSession(session) {
  return {
    id: session.id || session.conversationId,
    title: session.title || 'New conversation',
    masterPlayer: session.masterPlayer || 'PITCHER',
    createdAt: session.createdAt || Date.now(),
    turns: (session.turns || []).map(normalizeTurn),
  }
}

function mergeSessions(existing, incoming) {
  const byId = new Map(existing.map(session => [session.id, session]))

  incoming.forEach(session => {
    const current = byId.get(session.id)
    byId.set(session.id, current ? { ...session, turns: current.turns || session.turns || [] } : session)
  })

  return Array.from(byId.values())
}

export function useAsk() {
  const [sessions, setSessions] = useState([])
  const [activeId, setActiveId] = useState(null)
  const [playerStatus, setPlayerStatus] = useState({})
  const [playerTasks, setPlayerTasks] = useState({})
  const [loadingByConversation, setLoadingByConversation] = useState({})
  const [masterSelectPending, setMasterSelectPending] = useState(false)
  const [flowPhase, setFlowPhase] = useState(null)
  const [pendingDecomposition, setPendingDecomposition] = useState(null)
  const [awaitingConfirmation, setAwaitingConfirmation] = useState(false)
  const [clarificationOptions, setClarificationOptions] = useState(null)
  const [pendingMasterQuestion, setPendingMasterQuestion] = useState(null)
  const [detailRequest, setDetailRequest] = useState(null)
  const [currentMaster, setCurrentMaster] = useState('PITCHER')

  const abortControllersRef = useRef(new Map())
  const activeIdRef = useRef(null)
  const phaseRef = useRef(null)
  const masterRef = useRef('PITCHER')
  const sessionCreatingRef = useRef(false)
  const lastQuestionRef = useRef('')
  const lastTurnIdRef = useRef(null)
  const lastConvIdRef = useRef(null)

  useEffect(() => {
    let cancelled = false

    listSessions()
      .then(items => {
        if (cancelled) return
        const loaded = Array.isArray(items) ? items.map(normalizeSession) : []
        setSessions(prev => mergeSessions(prev, loaded))
      })
      .catch(() => {})

    return () => {
      cancelled = true
    }
  }, [])

  const persistSession = (session) => {
    if (!session?.id) return
    saveSession(session).catch(() => {})
  }

  const setActiveConversation = (conversationId) => {
    activeIdRef.current = conversationId
    setActiveId(conversationId)
  }

  const isActiveConversation = (conversationId) => activeIdRef.current === conversationId

  const setConversationLoading = (conversationId, loading) => {
    if (!conversationId) return
    setLoadingByConversation(prev => {
      const next = { ...prev }
      if (loading) {
        next[conversationId] = true
      } else {
        delete next[conversationId]
      }
      return next
    })
  }

  const setMaster = (masterPlayer) => {
    const nextMaster = masterPlayer || 'PITCHER'
    masterRef.current = nextMaster
    setCurrentMaster(nextMaster)
  }

  const updateTurn = (conversationId, turnId, updater) => {
    setSessions(prev => prev.map(session =>
      session.id !== conversationId
        ? session
        : { ...session, turns: session.turns.map(turn => turn.id === turnId ? updater(turn) : turn) }
    ))
  }

  const addVisiblePlayer = (conversationId, turnId, player) => {
    updateTurn(conversationId, turnId, turn => {
      if (turn.visiblePlayers?.includes(player)) return turn
      return { ...turn, visiblePlayers: [...(turn.visiblePlayers || []), player] }
    })
  }

  const resetFlow = () => {
    setFlowPhase(null)
    phaseRef.current = null
    setPendingDecomposition(null)
    setAwaitingConfirmation(false)
    setClarificationOptions(null)
  }

  const createRemoteSession = async (masterPlayer) => {
    sessionCreatingRef.current = true
    try {
      const session = await createSession(masterPlayer)
      return {
        id: session.conversationId,
        masterPlayer: session.masterPlayer || masterPlayer || 'PITCHER',
      }
    } finally {
      sessionCreatingRef.current = false
    }
  }

  const handlePlayerEvent = (msg, eventName, acc, conversationId, turnId) => {
    const player = msg.player || eventName
    addVisiblePlayer(conversationId, turnId, player)

    if (msg.status === 'STREAMING') {
      acc[player] = (acc[player] || '') + (msg.content || '')
      const key = phaseOutputKey(acc.phase)
      const content = acc[player]
      updateTurn(conversationId, turnId, turn => ({
        ...turn,
        answers: { ...turn.answers, [player]: content },
        phaseOutputs: {
          ...turn.phaseOutputs,
          [key]: { ...(turn.phaseOutputs?.[key] || {}), [player]: content },
        },
      }))
    } else if (msg.status === 'DONE') {
      if (isActiveConversation(conversationId)) {
        setPlayerStatus(prev => ({ ...prev, [player]: STATUS.DONE }))
        setPlayerTasks(prev => ({ ...prev, [player]: '' }))
      }
      if (msg.content) {
        acc[player] = msg.content
      }
      const key = phaseOutputKey(acc.phase)
      const content = acc[player]
      updateTurn(conversationId, turnId, turn => ({
        ...turn,
        answers: content ? { ...turn.answers, [player]: content } : turn.answers,
        phaseOutputs: content
          ? {
              ...turn.phaseOutputs,
              [key]: { ...(turn.phaseOutputs?.[key] || {}), [player]: content },
            }
          : turn.phaseOutputs,
        tokens: { ...turn.tokens, [player]: { in: msg.inputTokens, out: msg.outputTokens } },
      }))
    } else if (msg.status === 'ERROR') {
      if (isActiveConversation(conversationId)) {
        setPlayerStatus(prev => ({ ...prev, [player]: STATUS.ERROR }))
        setPlayerTasks(prev => ({ ...prev, [player]: '' }))
      }
    }
  }

  const handlePhaseEvent = (phase, acc, conversationId, turnId) => {
    const master = acc.master || masterRef.current
    const label = phaseTaskLabel(phase)

    acc.phase = phase
    if (isActiveConversation(conversationId)) {
      phaseRef.current = phase
      setFlowPhase(phase)
    }
    PLAYER_KEYS.forEach(key => { acc[key] = '' })
    updateTurn(conversationId, turnId, turn => ({ ...turn, currentPhase: phase }))

    if (!isActiveConversation(conversationId)) return

    if (PLAYER_WORK_PHASES.has(phase)) {
      setPlayerStatus(createEmptyStatus())
      setPlayerTasks({ PITCHER: label, CATCHER: label, FIELDER: label })
    } else if (MASTER_WORK_PHASES.has(phase)) {
      setPlayerStatus(prev => ({
        ...Object.fromEntries(Object.entries(prev).map(([key, value]) => [key, value === STATUS.THINKING ? STATUS.DONE : value])),
        [master]: STATUS.THINKING,
      }))
      setPlayerTasks(prev => ({ ...prev, [master]: label }))
    } else if (phase === PHASES.DECOMPOSING) {
      setPlayerStatus(prev => ({
        ...Object.fromEntries(Object.entries(prev).map(([key]) => [key, STATUS.IDLE])),
        [master]: STATUS.THINKING,
      }))
      setPlayerTasks({ ...createEmptyTasks(), [master]: label })
    }
  }

  const handleEvent = (eventName, msg, acc, conversationId, turnId) => {
    switch (eventName) {
      case 'PITCHER':
      case 'CATCHER':
      case 'FIELDER':
        handlePlayerEvent(msg, eventName, acc, conversationId, turnId)
        break

      case EVENT.PHASE:
        handlePhaseEvent(msg.phase, acc, conversationId, turnId)
        break

      case EVENT.INTENT:
        const intentType = msg.intentType || msg.intent
        const routedPlayers = msg.routedPlayers || playersForIntent(intentType, acc.master || masterRef.current)
        updateTurn(conversationId, turnId, turn => ({
          ...turn,
          intent: {
            intentType,
            confidence: msg.confidence,
            reason: msg.reason,
            routedPlayers,
            finalAnswerMode: msg.finalAnswerMode,
          },
          visiblePlayers: routedPlayers,
        }))
        break

      case EVENT.SYNTHESIS:
        acc.synthesis = (acc.synthesis || '') + (msg.chunk || '')
        updateTurn(conversationId, turnId, turn => ({
          ...turn,
          synthesis: acc.synthesis,
          phaseOutputs: { ...turn.phaseOutputs, synthesis: acc.synthesis },
        }))
        break

      case EVENT.DECOMPOSITION: {
        const subtasks = msg.subtasks || []
        if (isActiveConversation(conversationId)) {
          setPendingDecomposition({ subtasks, cid: conversationId })
        }
        updateTurn(conversationId, turnId, turn => ({ ...turn, subtasks }))
        if (isActiveConversation(conversationId)) {
          setPlayerTasks(Object.fromEntries(subtasks.map(task => [task.player, task.taskDescription])))
          setPlayerStatus(createEmptyStatus(STATUS.IDLE))
        }
        break
      }

      case EVENT.AWAITING_CONFIRMATION:
        setConversationLoading(conversationId, false)
        if (isActiveConversation(conversationId)) {
          setAwaitingConfirmation(true)
        }
        break

      case EVENT.CLARIFICATION:
        setConversationLoading(conversationId, false)
        if (isActiveConversation(conversationId)) {
          setClarificationOptions(msg.options || [])
        }
        break

      case EVENT.DONE:
        setConversationLoading(conversationId, false)
        abortControllersRef.current.delete(conversationId)
        if (isActiveConversation(conversationId)) {
          setFlowPhase(null)
          phaseRef.current = null
        }
        setSessions(prev => prev.map(session => {
          if (session.id !== conversationId) return session

          const updatedSession = {
            ...session,
            turns: session.turns.map(turn => turn.id === turnId ? { ...turn, currentPhase: null } : turn),
          }
          persistSession(updatedSession)
          return updatedSession
        }))
        if (isActiveConversation(conversationId)) {
          setPlayerStatus(prev =>
            Object.fromEntries(Object.entries(prev).map(([key, value]) => [key, value === STATUS.THINKING ? STATUS.DONE : value]))
          )
          setPlayerTasks(createEmptyTasks())
        }
        break
    }
  }

  const startStream = (body, turnId, conversationId) => {
    abortControllersRef.current.get(conversationId)?.abort()
    const controller = new AbortController()
    abortControllersRef.current.set(conversationId, controller)
    setConversationLoading(conversationId, true)
    const acc = { PITCHER: '', CATCHER: '', FIELDER: '', synthesis: '', phase: null, master: body.masterPlayer || masterRef.current }

    askStream(body, controller.signal).then(response => {
      if (!response.ok || !response.body) {
        throw new Error(`Ask request failed: ${response.status}`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      const parser = createSseParser((eventName, msg) => {
        handleEvent(eventName, msg, acc, conversationId, turnId)
      })

      function pump() {
        reader.read().then(({ done, value }) => {
          if (done) {
            setConversationLoading(conversationId, false)
            abortControllersRef.current.delete(conversationId)
            return
          }

          parser.push(decoder.decode(value, { stream: true }))
          pump()
        })
      }
      pump()
    }).catch(error => {
      setConversationLoading(conversationId, false)
      abortControllersRef.current.delete(conversationId)
      if (error.name === 'AbortError') return
      if (isActiveConversation(conversationId)) {
        setPlayerStatus(createEmptyStatus(STATUS.ERROR))
      }
    })
  }

  const ask = async (question) => {
    if ((activeId && loadingByConversation[activeId]) || sessionCreatingRef.current) return

    let conversationId = activeId
    let master = sessions.find(session => session.id === conversationId)?.masterPlayer || masterRef.current || 'PITCHER'

    if (!conversationId) {
      setPendingMasterQuestion(question)
      setMasterSelectPending(false)
      return
    }

    const turnId = `${conversationId}-${Date.now()}`
    const turn = createTurn(turnId, question)

    setSessions(prev => {
      const existing = prev.find(session => session.id === conversationId)
      if (existing) {
        return prev.map(session => session.id === conversationId
          ? { ...session, title: session.turns.length === 0 ? question : session.title, turns: [...session.turns, turn] }
          : session
        )
      }
      return [...prev, { id: conversationId, title: question, masterPlayer: master, createdAt: Date.now(), turns: [turn] }]
    })

    setActiveConversation(conversationId)
    setPlayerStatus(createEmptyStatus())
    setPlayerTasks(createEmptyTasks())
    resetFlow()

    setMaster(master)
    lastQuestionRef.current = question
    lastTurnIdRef.current = turnId
    lastConvIdRef.current = conversationId

    startStream({ question, conversationId, masterPlayer: master }, turnId, conversationId)
  }

  const cancel = () => {
    const conversationId = activeIdRef.current || lastConvIdRef.current
    abortControllersRef.current.get(conversationId)?.abort()
    abortControllersRef.current.delete(conversationId)

    if (conversationId) {
      cancelConversation(conversationId).catch(() => {})
      setConversationLoading(conversationId, false)
    }

    setAwaitingConfirmation(false)
    setClarificationOptions(null)
    setPlayerStatus(prev =>
      Object.fromEntries(Object.entries(prev).map(([key, value]) => [key, value === STATUS.THINKING ? STATUS.STOPPED : value]))
    )
  }

  const confirmPlan = () => {
    if (!pendingDecomposition) return
    const conversationId = lastConvIdRef.current
    const turnId = lastTurnIdRef.current
    if (!conversationId || !turnId) return

    setAwaitingConfirmation(false)
    setPendingDecomposition(null)
    phaseRef.current = null
    setPlayerStatus(createEmptyStatus())

    startStream({
      question: lastQuestionRef.current,
      conversationId,
      phase: 'CONFIRM_EXECUTE',
      confirmedPlan: pendingDecomposition.subtasks,
    }, turnId, conversationId)
  }

  const cancelPlan = () => {
    const conversationId = lastConvIdRef.current
    if (conversationId && pendingDecomposition) {
      cancelPendingPlan({ conversationId }).catch(() => {})
    }
    setAwaitingConfirmation(false)
    setPendingDecomposition(null)
    setConversationLoading(conversationId, false)
    setPlayerStatus({})
  }

  const selectIntent = (selectedIntent) => {
    const conversationId = lastConvIdRef.current
    const turnId = lastTurnIdRef.current
    if (!conversationId || !turnId) return

    setClarificationOptions(null)
    phaseRef.current = null
    setPlayerStatus(createEmptyStatus())

    startStream({
      question: lastQuestionRef.current,
      conversationId,
      phase: 'SELECT_INTENT',
      selectedIntent,
    }, turnId, conversationId)
  }

  const applyLoadedSession = (session) => {
    const normalized = normalizeSession(session)

    setSessions(prev => prev.map(item => item.id === normalized.id ? normalized : item))
    setActiveConversation(normalized.id)
    setMaster(normalized.masterPlayer || 'PITCHER')
    const lastTurn = [...(normalized.turns || [])].pop()
    setPlayerStatus(lastTurn
      ? Object.fromEntries(PLAYERS.map(player => [player.key, (lastTurn.answers?.[player.key] || lastTurn.synthesis) ? STATUS.DONE : STATUS.IDLE]))
      : {}
    )
    resetFlow()
  }

  const loadSession = async (session) => {
    const normalized = normalizeSession(session)

    if (normalized.turns.length > 0) {
      applyLoadedSession(normalized)
      return
    }

    setActiveConversation(normalized.id)
    setMaster(normalized.masterPlayer || 'PITCHER')
    setPlayerStatus({})
    resetFlow()

    try {
      const fullSession = await getSession(normalized.id)
      applyLoadedSession(fullSession)
    } catch (_) {}
  }

  const newSession = () => {
    setPendingMasterQuestion(null)
    setMasterSelectPending(true)
  }

  const createSessionWithMaster = async (masterPlayer) => {
    if (sessionCreatingRef.current) return

    try {
      const session = await createRemoteSession(masterPlayer)
      setActiveConversation(session.id)
      setMaster(session.masterPlayer)
      setMasterSelectPending(false)
    } catch (error) {
      setPlayerStatus(createEmptyStatus(STATUS.ERROR))
      return
    }
    setPlayerStatus({})
    setPlayerTasks({})
    resetFlow()
  }

  const cancelMasterSelect = () => setMasterSelectPending(false)

  const createSessionForPendingQuestion = async (masterPlayer) => {
    if (sessionCreatingRef.current || !pendingMasterQuestion) return

    const question = pendingMasterQuestion
    try {
      const session = await createRemoteSession(masterPlayer)
      const conversationId = session.id
      const master = session.masterPlayer
      const turnId = `${conversationId}-${Date.now()}`
      const turn = createTurn(turnId, question)

      setSessions(prev => [...prev, { id: conversationId, title: question, masterPlayer: master, createdAt: Date.now(), turns: [turn] }])
      setActiveConversation(conversationId)
      setPendingMasterQuestion(null)
      setPlayerStatus(createEmptyStatus())
      setPlayerTasks(createEmptyTasks())
      resetFlow()

      setMaster(master)
      lastQuestionRef.current = question
      lastTurnIdRef.current = turnId
      lastConvIdRef.current = conversationId

      startStream({ question, conversationId, masterPlayer: master }, turnId, conversationId)
    } catch (error) {
      setPlayerStatus(createEmptyStatus(STATUS.ERROR))
    }
  }

  const cancelPendingMasterQuestion = () => {
    setPendingMasterQuestion(null)
  }

  const showPlayerDetail = (player) => {
    setDetailRequest({ player, id: Date.now() })
  }

  const activeSession = sessions.find(session => session.id === activeId) || null
  const activeTurn = activeSession ? [...(activeSession.turns || [])].pop() : null
  const isLoading = activeId ? !!loadingByConversation[activeId] : false

  return {
    sessions,
    activeId,
    activeSession,
    masterPlayer: activeSession?.masterPlayer || currentMaster,
    streaming: activeTurn?.answers || {},
    tokens: activeTurn?.tokens || {},
    playerStatus,
    playerTasks,
    isLoading,
    flowPhase,
    pendingDecomposition,
    awaitingConfirmation,
    clarificationOptions,
    pendingMasterQuestion,
    detailRequest,
    ask,
    cancel,
    confirmPlan,
    cancelPlan,
    selectIntent,
    loadSession,
    newSession,
    masterSelectPending,
    createSessionWithMaster,
    cancelMasterSelect,
    createSessionForPendingQuestion,
    cancelPendingMasterQuestion,
    showPlayerDetail,
  }
}
