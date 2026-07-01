import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ClarificationCard } from './ClarificationCard'

describe('ClarificationCard', () => {
  it('maps options to analyze and execute intents', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()

    render(<ClarificationCard options={['Analyse this', 'Execute this']} onSelect={onSelect} />)

    await user.click(screen.getByRole('button', { name: /Analyse this/ }))
    await user.click(screen.getByRole('button', { name: /Execute this/ }))

    expect(onSelect).toHaveBeenNthCalledWith(1, 'ANALYZE')
    expect(onSelect).toHaveBeenNthCalledWith(2, 'EXECUTE')
  })
})
