import { API_BASE } from '../constants/flow'

export function askStream(body, signal) {
  return fetch(`${API_BASE}/ask`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  })
}

export async function createSession(masterPlayer) {
  const response = await fetch(`${API_BASE}/session`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(masterPlayer ? { masterPlayer } : {}),
  })

  if (!response.ok) {
    throw new Error(`Failed to create session: ${response.status}`)
  }

  return response.json()
}

export async function listSessions() {
  const response = await fetch(`${API_BASE}/sessions`)

  if (!response.ok) {
    throw new Error(`Failed to list sessions: ${response.status}`)
  }

  return response.json()
}

export async function getSession(sessionId) {
  const response = await fetch(`${API_BASE}/sessions/${encodeURIComponent(sessionId)}`)

  if (!response.ok) {
    throw new Error(`Failed to load session: ${response.status}`)
  }

  return response.json()
}

export function saveSession(session) {
  return fetch(`${API_BASE}/sessions/${encodeURIComponent(session.id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(session),
  })
}

export function deleteSession(sessionId) {
  return fetch(`${API_BASE}/sessions/${encodeURIComponent(sessionId)}`, {
    method: 'DELETE',
  })
}

export function cancelConversation(conversationId) {
  return fetch(`${API_BASE}/cancel`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId }),
  })
}

export function cancelPendingPlan({ conversationId }) {
  return fetch(`${API_BASE}/ask`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      question: '',
      conversationId,
      phase: 'CANCEL',
    }),
  })
}
