import { expect, test } from '@playwright/test'

function sseEvent(event, data) {
  return `event:${event}\ndata:${typeof data === 'string' ? data : JSON.stringify(data)}\n\n`
}

test('runs an analyze flow with mocked SSE responses', async ({ page }) => {
  await page.route('http://localhost:8080/api/sessions', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { id: 'historical-session', title: 'Loaded history', masterPlayer: 'FIELDER', createdAt: Date.now() },
      ]),
    })
  })

  await page.route('http://localhost:8080/api/session', async route => {
    expect(route.request().postDataJSON()).toEqual({ masterPlayer: 'PITCHER' })
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ conversationId: 'test-conversation', masterPlayer: 'PITCHER' }),
    })
  })

  await page.route('http://localhost:8080/api/ask', async route => {
    expect(route.request().postDataJSON()).toEqual({
      question: 'Should we launch this feature?',
      conversationId: 'test-conversation',
      masterPlayer: 'PITCHER',
    })

    const body = [
      sseEvent('session', { conversationId: 'test-conversation' }),
      sseEvent('intent', { intent: 'ANALYZE', reason: 'The user asks for analysis.' }),
      sseEvent('phase', { phase: 'round1' }),
      sseEvent('pitcher', { player: 'PITCHER', status: 'STREAMING', content: 'Pitcher analysis.', inputTokens: 0, outputTokens: 0 }),
      sseEvent('pitcher', { player: 'PITCHER', status: 'DONE', content: 'Pitcher analysis.', inputTokens: 4, outputTokens: 6 }),
      sseEvent('phase', { phase: 'synthesis' }),
      sseEvent('synthesis', { chunk: 'Final recommendation.' }),
      sseEvent('done', 'analysis complete'),
    ].join('')

    await new Promise(resolve => setTimeout(resolve, 50))

    await route.fulfill({
      status: 200,
      headers: {
        'content-type': 'text/event-stream',
        'cache-control': 'no-cache',
      },
      body,
    })
  })

  await page.route('http://localhost:8080/api/sessions/test-conversation', async route => {
    expect(route.request().method()).toBe('PUT')
    const saved = route.request().postDataJSON()
    expect(saved.id).toBe('test-conversation')
    expect(saved.masterPlayer).toBe('PITCHER')
    expect(saved.turns[0].question).toBe('Should we launch this feature?')
    expect(saved.turns[0].answers.PITCHER).toBe('Pitcher analysis.')
    await route.fulfill({ status: 204 })
  })

  await page.route('http://localhost:8080/api/cancel', async route => {
    await route.fulfill({ status: 204 })
  })

  await page.goto('/')

  await expect(page.getByText('Loaded history')).toBeVisible()
  await page.getByPlaceholder(/Ask anything/).fill('Should we launch this feature?')
  await page.getByRole('button', { name: /Send/ }).click()
  await expect(page.getByText('Select a Master')).toBeVisible()
  await page.getByRole('button', { name: /Pitcher \(Claude\)/ }).click()

  const mainPanel = page.locator('.main-panel')

  await expect(mainPanel.getByText('Should we launch this feature?')).toBeVisible()
  await expect(mainPanel.getByText('Analyze', { exact: true })).toBeVisible()
  await expect(page.getByText('Final Conclusion')).toBeVisible()
  await expect(page.getByText('Final recommendation.')).toBeVisible()
  await page.locator('.task-panel').getByRole('button', { name: /Details/ }).first().click()
  await expect(mainPanel.getByText('Pitcher analysis.')).toBeVisible()
  await mainPanel.getByRole('button', { name: /Pitcher \(Claude\).*Collapse/ }).click()
  await expect(mainPanel.getByText('Pitcher analysis.')).toBeHidden()
})

test('loads a persisted session when a history item is selected', async ({ page }) => {
  await page.route('http://localhost:8080/api/sessions', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { id: 'historical-session', title: 'Loaded history', masterPlayer: 'FIELDER', createdAt: Date.now() },
      ]),
    })
  })

  await page.route('http://localhost:8080/api/sessions/historical-session', async route => {
    expect(route.request().method()).toBe('GET')
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'historical-session',
        title: 'Loaded history',
        masterPlayer: 'FIELDER',
        createdAt: Date.now(),
        turns: [
          {
            id: 'turn-history',
            question: 'What happened yesterday?',
            answers: { FIELDER: 'Historical answer.' },
            tokens: { FIELDER: { in: 10, out: 4 } },
            intent: { intentType: 'SEARCH', routedPlayers: ['FIELDER'], finalAnswerMode: 'SINGLE_PLAYER' },
            synthesis: '',
          },
        ],
      }),
    })
  })

  await page.goto('/')

  await page.getByText('Loaded history').click()

  const mainPanel = page.locator('.main-panel')
  await expect(mainPanel.getByText('What happened yesterday?')).toBeVisible()
  await expect(mainPanel.getByText('Historical answer.')).toBeVisible()
})
