import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MarkdownText } from './MarkdownText'

describe('MarkdownText', () => {
  it('renders common markdown blocks without raw markdown markers', () => {
    render(
      <MarkdownText
        text={'## Summary\n\n**Bold** item with `code`\n\n- First\n- Second\n\n```js\nconst x = 1\n```'}
      />,
    )

    expect(screen.getByRole('heading', { name: 'Summary' })).toBeInTheDocument()
    expect(screen.getByText('Bold').tagName).toBe('STRONG')
    expect(screen.getByText('code').tagName).toBe('CODE')
    expect(screen.getByText('First')).toBeInTheDocument()
    expect(screen.getByText('const x = 1')).toBeInTheDocument()
  })
})
