export const PLAYERS = [
  { key: 'PITCHER', role: 'Pitcher', llm: 'Claude', label: 'Pitcher (Claude)', emoji: '⚾', color: '#4f46e5' },
  { key: 'CATCHER', role: 'Catcher', llm: 'Agy', label: 'Catcher (Agy)', emoji: '🧤', color: '#0891b2' },
  { key: 'FIELDER', role: 'Fielder', llm: 'Codex', label: 'Fielder (Codex)', emoji: '🏟️', color: '#059669' },
]

export const PLAYER_MAP = Object.fromEntries(PLAYERS.map(p => [p.key, p]))

export const PLAYER_KEYS = PLAYERS.map(p => p.key)
