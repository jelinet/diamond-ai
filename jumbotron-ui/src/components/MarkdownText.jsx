function parseInline(text) {
  const parts = []
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`)/g
  let lastIndex = 0
  let match

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(text.slice(lastIndex, match.index))
    }

    const value = match[0]
    if (value.startsWith('**')) {
      parts.push(<strong key={parts.length}>{value.slice(2, -2)}</strong>)
    } else {
      parts.push(<code key={parts.length}>{value.slice(1, -1)}</code>)
    }
    lastIndex = match.index + value.length
  }

  if (lastIndex < text.length) {
    parts.push(text.slice(lastIndex))
  }

  return parts
}

function flushParagraph(blocks, lines) {
  if (lines.length === 0) return
  blocks.push({ type: 'paragraph', text: lines.join('\n') })
  lines.length = 0
}

function parseMarkdown(text) {
  const blocks = []
  const paragraphLines = []
  const lines = text.split('\n')
  let i = 0

  while (i < lines.length) {
    const line = lines[i]

    if (line.trim() === '') {
      flushParagraph(blocks, paragraphLines)
      i += 1
      continue
    }

    if (line.trim().startsWith('```')) {
      flushParagraph(blocks, paragraphLines)
      const codeLines = []
      i += 1
      while (i < lines.length && !lines[i].trim().startsWith('```')) {
        codeLines.push(lines[i])
        i += 1
      }
      blocks.push({ type: 'code', text: codeLines.join('\n') })
      i += 1
      continue
    }

    const heading = line.match(/^(#{1,3})\s+(.+)$/)
    if (heading) {
      flushParagraph(blocks, paragraphLines)
      blocks.push({ type: 'heading', level: heading[1].length, text: heading[2] })
      i += 1
      continue
    }

    const unordered = line.match(/^\s*[-*]\s+(.+)$/)
    if (unordered) {
      flushParagraph(blocks, paragraphLines)
      const items = []
      while (i < lines.length) {
        const item = lines[i].match(/^\s*[-*]\s+(.+)$/)
        if (!item) break
        items.push(item[1])
        i += 1
      }
      blocks.push({ type: 'ul', items })
      continue
    }

    const ordered = line.match(/^\s*\d+\.\s+(.+)$/)
    if (ordered) {
      flushParagraph(blocks, paragraphLines)
      const items = []
      while (i < lines.length) {
        const item = lines[i].match(/^\s*\d+\.\s+(.+)$/)
        if (!item) break
        items.push(item[1])
        i += 1
      }
      blocks.push({ type: 'ol', items })
      continue
    }

    paragraphLines.push(line)
    i += 1
  }

  flushParagraph(blocks, paragraphLines)
  return blocks
}

export function MarkdownText({ text, className = '' }) {
  const blocks = parseMarkdown(text || '')

  return (
    <div className={`markdown-text ${className}`}>
      {blocks.map((block, index) => {
        if (block.type === 'heading') {
          const Tag = `h${block.level + 2}`
          return <Tag key={index}>{parseInline(block.text)}</Tag>
        }

        if (block.type === 'code') {
          return <pre key={index}><code>{block.text}</code></pre>
        }

        if (block.type === 'ul') {
          return (
            <ul key={index}>
              {block.items.map((item, itemIndex) => <li key={itemIndex}>{parseInline(item)}</li>)}
            </ul>
          )
        }

        if (block.type === 'ol') {
          return (
            <ol key={index}>
              {block.items.map((item, itemIndex) => <li key={itemIndex}>{parseInline(item)}</li>)}
            </ol>
          )
        }

        return <p key={index}>{parseInline(block.text)}</p>
      })}
    </div>
  )
}
