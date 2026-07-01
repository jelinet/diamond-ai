import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { TurnBlock } from './TurnBlock'

describe('TurnBlock', () => {
  it('does not render an empty intent badge for search questions', () => {
    render(
      <TurnBlock
        turn={{
          id: 'turn-search',
          question: 'Search this topic',
          intent: { intentType: 'SEARCH' },
          answers: {},
          visiblePlayers: [],
        }}
        isCurrent={false}
        playerStatus={{}}
        masterPlayer="PITCHER"
      />
    )

    expect(screen.getByText('Search this topic')).toBeVisible()
    expect(document.querySelector('.intent-badge')).not.toBeInTheDocument()
  })

  it('uses the shared disclosure icon for player expand and collapse controls', async () => {
    const user = userEvent.setup()

    render(
      <TurnBlock
        turn={{
          id: 'turn-player',
          question: 'Explain this',
          intent: null,
          answers: { PITCHER: 'Pitcher answer.' },
          visiblePlayers: ['PITCHER'],
        }}
        isCurrent={false}
        playerStatus={{}}
        masterPlayer="PITCHER"
      />
    )

    expect(screen.queryByText('Expand')).not.toBeInTheDocument()
    expect(screen.queryByText('Collapse')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Pitcher \(Claude\).*Expand/ }))

    expect(screen.getByRole('button', { name: /Pitcher \(Claude\).*Collapse/ })).toBeVisible()
    expect(document.querySelector('.process-tab-toggle .disclosure-chevron')).toBeInTheDocument()
  })
})
