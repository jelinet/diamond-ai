import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { MasterSelectPanel } from './MasterSelectPanel'

describe('MasterSelectPanel', () => {
  it('selects the requested master player', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()

    render(<MasterSelectPanel onSelect={onSelect} onCancel={() => {}} />)

    await user.click(screen.getByRole('button', { name: /Fielder \(Codex\)/ }))

    expect(onSelect).toHaveBeenCalledWith('FIELDER')
  })

  it('can cancel master selection', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()

    render(<MasterSelectPanel onSelect={() => {}} onCancel={onCancel} />)

    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onCancel).toHaveBeenCalledOnce()
  })
})
