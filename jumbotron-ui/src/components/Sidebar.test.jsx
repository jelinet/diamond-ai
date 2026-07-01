import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Sidebar } from './Sidebar'

function renderSidebar(sessions) {
  return render(
    <Sidebar
      sessions={sessions}
      activeId={null}
      onSelect={() => {}}
      onNew={() => {}}
      onSearch={() => {}}
      masterSelectPending={false}
      onMasterSelect={() => {}}
      onCancelMasterSelect={() => {}}
    />
  )
}

describe('Sidebar', () => {
  it('shows only chats from the last three months by default', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-02T00:00:00Z'))

    renderSidebar([
      { id: 'recent', title: 'Recent chat', createdAt: new Date('2026-05-02T00:00:00Z').getTime(), turns: [{}] },
      { id: 'old', title: 'Old chat', createdAt: new Date('2026-02-01T00:00:00Z').getTime(), turns: [{}] },
    ])

    expect(screen.getByText('Recent chat')).toBeVisible()
    expect(screen.queryByText('Old chat')).not.toBeInTheDocument()

    vi.useRealTimers()
  })

  it('keeps new and search chat controls outside the scrollable history', () => {
    renderSidebar([{ id: 'recent', title: 'Recent chat', createdAt: Date.now(), turns: [{}] }])

    expect(screen.getByRole('button', { name: /New chat/ }).closest('.sidebar-header')).toBeTruthy()
    expect(screen.getByRole('button', { name: /Search chats/ }).closest('.sidebar-header')).toBeTruthy()
    expect(screen.getByText('Recent chat').closest('.session-history')).toBeTruthy()
  })

  it('opens chat search from the fixed sidebar control', async () => {
    const user = userEvent.setup()
    const onSearch = vi.fn()

    render(
      <Sidebar
        sessions={[]}
        activeId={null}
        onSelect={() => {}}
        onNew={() => {}}
        onSearch={onSearch}
        masterSelectPending={false}
        onMasterSelect={() => {}}
        onCancelMasterSelect={() => {}}
      />
    )

    await user.click(screen.getByRole('button', { name: /Search chats/ }))

    expect(onSearch).toHaveBeenCalledOnce()
  })
})
