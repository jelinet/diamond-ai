export const API_BASE = 'http://localhost:8080/api'

export const PHASES = {
  ROUND_1: 'round1',
  ROUND_2: 'round2',
  SYNTHESIS: 'synthesis',
  DECOMPOSING: 'decomposing',
  EXECUTING: 'executing',
  ASSEMBLING: 'assembling',
  ANALYZING: 'analyzing',
  ANSWERING: 'answering',
}

export const PHASE_TASK_LABEL = {
  [PHASES.ROUND_1]: 'Round 1 · Analysis',
  [PHASES.ROUND_2]: 'Round 2 · Debate',
  [PHASES.SYNTHESIS]: 'Synthesis',
  [PHASES.DECOMPOSING]: 'Decomposing task',
  [PHASES.ASSEMBLING]: 'Assembling summary',
  [PHASES.EXECUTING]: 'Executing subtasks',
  [PHASES.ANALYZING]: 'Analyzing',
  [PHASES.ANSWERING]: 'Answering',
}

export const PHASE_PANEL_LABEL = {
  [PHASES.ROUND_1]: 'Round 1',
  [PHASES.ROUND_2]: 'Round 2',
  [PHASES.SYNTHESIS]: 'Synthesis',
  [PHASES.DECOMPOSING]: 'Planning',
  [PHASES.EXECUTING]: 'Executing',
  [PHASES.ASSEMBLING]: 'Assembling',
}

export const PLAYER_WORK_PHASES = new Set([
  PHASES.ROUND_1,
  PHASES.ROUND_2,
  PHASES.EXECUTING,
  PHASES.ANALYZING,
  PHASES.ANSWERING,
])

export const MASTER_WORK_PHASES = new Set([
  PHASES.SYNTHESIS,
  PHASES.ASSEMBLING,
])

export const EVENT = {
  PHASE: 'PHASE',
  INTENT: 'INTENT',
  SYNTHESIS: 'SYNTHESIS',
  DECOMPOSITION: 'DECOMPOSITION',
  AWAITING_CONFIRMATION: 'AWAITING_CONFIRMATION',
  CLARIFICATION: 'CLARIFICATION',
  DONE: 'DONE',
}

export const STATUS = {
  IDLE: 'idle',
  THINKING: 'thinking',
  DONE: 'done',
  ERROR: 'error',
  STOPPED: 'stopped',
}

export const STATUS_LABEL = {
  [STATUS.IDLE]: 'Idle',
  [STATUS.THINKING]: 'Working',
  [STATUS.DONE]: 'Done',
  [STATUS.ERROR]: 'Error',
  [STATUS.STOPPED]: 'Stopped',
}

export const INTENT_BY_OPTION_INDEX = ['ANALYZE', 'EXECUTE']
