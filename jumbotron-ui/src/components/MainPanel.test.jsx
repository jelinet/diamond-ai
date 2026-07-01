import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { searchSessions } from '../services/chatApi'
import { MainPanel } from './MainPanel'

vi.mock('../services/chatApi', () => ({
  searchSessions: vi.fn(),
}))

function renderMainPanel(props = {}) {
  return render(<MainPanel {...mainPanelProps(props)} />)
}

function mainPanelProps(props = {}) {
  return {
    searchOpen: false,
    sessions: [],
    activeSession: { turns: [] },
    playerStatus: {},
    isLoading: false,
    onSelectSession: () => {},
    onAsk: () => {},
    onCancel: () => {},
    pendingDecomposition: null,
    awaitingConfirmation: false,
    clarificationOptions: null,
    pendingMasterQuestion: null,
    detailRequest: null,
    onConfirmPlan: () => {},
    onCancelPlan: () => {},
    onSelectIntent: () => {},
    onMasterSelectForQuestion: () => {},
    onCancelMasterQuestion: () => {},
    masterPlayer: 'PITCHER',
    ...props,
  }
}

describe('MainPanel', () => {
  beforeEach(() => {
    searchSessions.mockResolvedValue([])
  })

  it('does not submit Enter while an IME composition is active', () => {
    const onAsk = vi.fn()
    renderMainPanel({ onAsk })

    const input = screen.getByPlaceholderText(/Enter your question/)
    fireEvent.change(input, { target: { value: 'a' } })
    fireEvent.compositionStart(input)
    fireEvent.keyDown(input, { key: 'Enter' })
    fireEvent.compositionEnd(input)

    expect(onAsk).not.toHaveBeenCalled()
    expect(input).toHaveValue('a')
  })

  it('submits with Enter after text input is committed', () => {
    const onAsk = vi.fn()
    renderMainPanel({ onAsk })

    const input = screen.getByPlaceholderText(/Enter your question/)
    fireEvent.change(input, { target: { value: 'hello' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(onAsk).toHaveBeenCalledWith('hello')
    expect(input).toHaveValue('')
  })

  it('loads recent chats from the backend in search mode and opens a selected session', async () => {
    const onSelectSession = vi.fn()
    const sessions = [
      { id: 'session-1', title: 'Recent chat', createdAt: Date.UTC(2026, 6, 2), lastQuestion: 'Latest question' },
      { id: 'session-2', title: 'Older chat', createdAt: Date.UTC(2026, 5, 30), lastQuestion: 'Earlier question' },
    ]
    searchSessions.mockResolvedValueOnce(sessions)

    renderMainPanel({
      searchOpen: true,
      onSelectSession,
    })

    expect(screen.getByPlaceholderText('Search chats')).toBeVisible()
    expect(await screen.findByText('Recent chat')).toBeVisible()
    expect(screen.getByText('Latest question')).toBeVisible()
    expect(screen.getByText('7/2')).toBeVisible()
    expect(searchSessions).toHaveBeenCalledWith('', 10)

    await userEvent.click(screen.getByRole('button', { name: /Recent chat/ }))

    expect(onSelectSession).toHaveBeenCalledWith(sessions[0])
  })

  it('searches chats through the backend as the query changes', async () => {
    const user = userEvent.setup()
    searchSessions.mockImplementation(query => Promise.resolve(
      query === 'bug'
        ? [{ id: 'b', title: 'Debugging', createdAt: 2, lastQuestion: 'Find search bug' }]
        : []
    ))

    renderMainPanel({
      searchOpen: true,
    })

    await user.type(screen.getByRole('searchbox'), 'bug')

    await waitFor(() => {
      expect(searchSessions).toHaveBeenCalledWith('b', 10)
      expect(searchSessions).toHaveBeenCalledWith('bu', 10)
      expect(searchSessions).toHaveBeenCalledWith('bug', 10)
    })
    expect(await screen.findByText('Debugging')).toBeVisible()
  })

  it('clears the search box and requests recent chats when the clear button is clicked', async () => {
    const user = userEvent.setup()
    searchSessions.mockImplementation(query => Promise.resolve(
      query === ''
        ? [{ id: 'recent', title: 'Recent chat', createdAt: 1 }]
        : []
    ))

    renderMainPanel({ searchOpen: true })

    const input = screen.getByRole('searchbox')
    await user.type(input, 'debug')
    await user.click(screen.getByRole('button', { name: 'Clear search' }))

    expect(input).toHaveValue('')
    await waitFor(() => expect(searchSessions).toHaveBeenLastCalledWith('', 10))
    expect(await screen.findByText('Recent chat')).toBeVisible()
  })

  it('clears the previous query when search mode is opened again', async () => {
    const user = userEvent.setup()
    searchSessions.mockResolvedValue([])

    const { rerender } = render(<MainPanel {...mainPanelProps({ searchOpen: true })} />)
    await user.type(screen.getByRole('searchbox'), 'bug')
    expect(screen.getByRole('searchbox')).toHaveValue('bug')

    rerender(<MainPanel {...mainPanelProps({ searchOpen: false })} />)
    rerender(<MainPanel {...mainPanelProps({ searchOpen: true })} />)

    const input = screen.getByRole('searchbox')
    await waitFor(() => expect(input).toHaveValue(''))
    await waitFor(() => expect(searchSessions).toHaveBeenLastCalledWith('', 10))
  })
})
