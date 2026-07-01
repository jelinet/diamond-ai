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
        playerTasks={{ CATCHER: 'Round 1 · 初始分析' }}
        streaming={{ PITCHER: 'Structured answer', CATCHER: 'Partial answer' }}
        tokens={{ PITCHER: { in: 3, out: 5 } }}
        masterPlayer="PITCHER"
        flowPhase="round1"
        onShowPlayerDetail={onShowPlayerDetail}
      />,
    )

    expect(screen.getByText('Round 1')).toBeInTheDocument()
    expect(screen.getByText(/Pitcher/).closest('.task-card')).toHaveClass('master')
    expect(screen.getByText('LLM · Claude')).toBeInTheDocument()
    expect(screen.getByText('MASTER')).toBeInTheDocument()
    expect(screen.getByText('Round 1 · 初始分析')).toBeInTheDocument()
    expect(screen.getByText('8')).toBeInTheDocument()

    await user.click(screen.getByText(/Pitcher/).closest('.task-card-top'))

    expect(onShowPlayerDetail).toHaveBeenCalledWith('PITCHER')
  })
})
