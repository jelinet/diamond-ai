export function createSseParser(onEvent) {
  let buffer = ''
  let eventName = ''

  return {
    push(chunk) {
      buffer += chunk
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''

      for (const rawLine of lines) {
        const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine

        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim().toUpperCase()
          continue
        }

        if (line.startsWith('data:')) {
          const raw = line.slice(5).trim()
          if (!raw) {
            eventName = ''
            continue
          }

          let data = raw
          try {
            data = JSON.parse(raw)
          } catch (_) {}

          onEvent(eventName, data)
          eventName = ''
        }
      }
    },
  }
}
