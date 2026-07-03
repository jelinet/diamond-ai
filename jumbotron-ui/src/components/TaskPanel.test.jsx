import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { TaskPanel } from './TaskPanel'

describe('TaskPanel', () => {
  it('shows player status, phase, token counts, and opens detail in the main panel', async () => {
    const user = userEvent.setup()
    const onShowPlayerDetail = vi.fn()

    render(
      <TaskPanel
        playerStatus={{ PITCHER: 'done', CATCHER: 'thinking', FIELDER: 'idle' }}
        playerTasks={{ CATCHER: 'Round 1 · Analysis' }}
        playerErrors={{}}
        streaming={{ PITCHER: 'Structured answer', CATCHER: 'Partial answer' }}
        tokens={{ PITCHER: { in: 3, out: 5 } }}
        masterPlayer="PITCHER"
        flowPhase="round1"
        supervisor={{ state: 'RUNNING_PLAYERS' }}
        onShowPlayerDetail={onShowPlayerDetail}
      />,
    )

    expect(screen.getByText('Running')).toBeInTheDocument()
    expect(screen.getByText('Round 1')).toBeInTheDocument()
    expect(screen.getByText(/Pitcher/).closest('.task-card')).toHaveClass('master')
    expect(screen.getByText('LLM · Claude')).toBeInTheDocument()
    expect(screen.getByText('MASTER')).toBeInTheDocument()
    expect(screen.getByText('Round 1 · Analysis')).toBeInTheDocument()
    expect(screen.getByText('8')).toBeInTheDocument()

    await user.click(screen.getByText(/Pitcher/).closest('.task-card-top'))

    expect(onShowPlayerDetail).toHaveBeenCalledWith('PITCHER')
  })

  it('shows session token totals for an idle player that responded in earlier turns', () => {
    render(
      <TaskPanel
        playerStatus={{ PITCHER: 'idle', CATCHER: 'idle', FIELDER: 'idle' }}
        playerTasks={{}}
        playerErrors={{}}
        streaming={{}}
        tokens={{ PITCHER: { in: 12, out: 8 } }}
        masterPlayer="PITCHER"
        flowPhase={null}
        onShowPlayerDetail={() => {}}
      />,
    )

    const pitcherCard = screen.getByText(/Pitcher/).closest('.task-card')
    expect(pitcherCard).toHaveTextContent('Input')
    expect(pitcherCard).toHaveTextContent('12')
    expect(pitcherCard).toHaveTextContent('Output')
    expect(pitcherCard).toHaveTextContent('8')
    expect(pitcherCard).toHaveTextContent('Total')
    expect(pitcherCard).toHaveTextContent('20')
  })

  it('shows supervisor error messages for failed players', () => {
    render(
      <TaskPanel
        playerStatus={{ PITCHER: 'error', CATCHER: 'idle', FIELDER: 'idle' }}
        playerTasks={{}}
        playerErrors={{ PITCHER: 'CLI timed out' }}
        streaming={{}}
        tokens={{}}
        masterPlayer="PITCHER"
        flowPhase="round1"
        onShowPlayerDetail={() => {}}
      />,
    )

    expect(screen.getByText('CLI timed out')).toBeInTheDocument()
  })
})
