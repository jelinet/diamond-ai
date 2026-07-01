import { describe, expect, it, vi } from 'vitest'
import { createSseParser } from './sse'

describe('createSseParser', () => {
  it('keeps event names when event and data arrive in different chunks', () => {
    const onEvent = vi.fn()
    const parser = createSseParser(onEvent)

    parser.push('event:pitcher\n')
    parser.push('data:{"player":"PITCHER","status":"STREAMING","content":"hello"}\n\n')

    expect(onEvent).toHaveBeenCalledWith('PITCHER', {
      player: 'PITCHER',
      status: 'STREAMING',
      content: 'hello',
    })
  })
})
