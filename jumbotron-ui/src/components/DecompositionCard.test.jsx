import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { DecompositionCard } from './DecompositionCard'

const decomposition = {
  subtasks: [
    { player: 'PITCHER', taskDescription: 'Structure the plan', deliverable: 'Outline' },
    { player: 'CATCHER', taskDescription: 'Find risks', deliverable: 'Risk list' },
  ],
}

describe('DecompositionCard', () => {
  it('shows subtasks and handles confirmation actions', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    const onCancel = vi.fn()

    render(<DecompositionCard decomposition={decomposition} onConfirm={onConfirm} onCancel={onCancel} />)

    expect(screen.getByText('Structure the plan')).toBeInTheDocument()
    expect(screen.getByText('产出：Risk list')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Confirmed,commencing execution./ }))
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onConfirm).toHaveBeenCalledOnce()
    expect(onCancel).toHaveBeenCalledOnce()
  })
})
