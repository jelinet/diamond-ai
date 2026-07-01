import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SynthesisCard } from './SynthesisCard'

describe('SynthesisCard', () => {
  it('does not show a blinking cursor after streamed synthesis text', () => {
    render(
      <SynthesisCard
        text="Final answer."
        isStreaming={true}
        masterPlayer="PITCHER"
      />
    )

    expect(screen.getByText('generating…')).toBeVisible()
    expect(screen.getByText('Final answer.')).toBeVisible()
    expect(document.querySelector('.synthesis-body .cursor')).not.toBeInTheDocument()
  })
})
