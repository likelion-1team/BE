import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'

// Verify the real local API, never the FE simulator or the deployed service.
const base = 'http://127.0.0.1:8081/api/v1'
const marker = await fetch(base + '/users/me')
assert.equal(marker.headers.get('X-Toki-Demo'), 'local-synthetic', 'Demo profile marker required')
const seed = JSON.parse(await readFile('demo-fixtures/local-demo-result.json', 'utf8'))
assert.equal(seed.model, 'demo-local')
assert.equal(seed.analyses.length, 6)
const sessions = []
const checks = []
async function request(path, token, body, method = body == null ? 'GET' : 'POST') {
  const response = await fetch(base + path, {
    method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body == null ? {} : { 'Content-Type': 'application/json' }) },
    body: body == null ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  return { status: response.status, result: text ? JSON.parse(text) : {} }
}
async function api(path, token, body, method) {
  const response = await request(path, token, body, method)
  assert.ok(response.status >= 200 && response.status < 300,
    `${path}: ${response.status} ${response.result.code}`)
  return response.result.data
}
const teamPath = `/teams/${seed.teamId}`
const teamQuery = '/dashboard/kpis?scope=TEAM&settingsVersion=1&weekStart=2026-11-16'
const meQuery = '/dashboard/kpis?scope=ME&settingsVersion=1&from=2026-11-01&to=2026-11-30'
const metric = (kpis, code) => kpis.metrics.find(item => item.code === code)
try {
  let tokens = 0
  const conversations = new Set()
  for (let index = 0; index < 6; index++) {
    const email = index ? `demo.member${index}@local.test` : 'demo.leader@local.test'
    const login = await api('/auth/login', null, { email, password: 'DemoOnly1234!' })
    assert.ok(login.accessToken)
    sessions.push(login.accessToken)
    const token = login.accessToken
    const items = await api(`/analyses/${seed.analyses[index]}/items?page=1&size=100`, token)
    assert.equal(items.total, 4)
    for (const item of items.items) {
      assert.equal(item.status, 'SUCCEEDED')
      assert.equal(item.aiAssessment.outcome, 'UNKNOWN', 'Synthetic AI must not assert business success')
      assert.ok(!conversations.has(item.conversationId), 'Duplicate conversation in analysis')
      conversations.add(item.conversationId)
    }
    const day = await api(teamPath + '/days/2026-11-18', token)
    assert.equal(day.conversations.length, 4, 'Day detail must only contain the caller\'s conversations')
    const ownIds = new Set(items.items.map(item => item.conversationId))
    for (const row of day.conversations) {
      assert.ok(ownIds.has(row.conversationId))
      tokens += row.estimatedTokens
    }
    if (index) assert.equal(day.taskTypes, null)
    const me = await api(teamPath + meQuery, token)
    assert.equal(me.coverage.selectedCount, 4)
    assert.equal(metric(me, 'confirmedSuccessRate').status, 'INSUFFICIENT_SAMPLE')
    assert.equal(metric(me, 'confirmedSuccessRate').value, null, 'Small samples must not display a rate')
    checks.push(`role-${index}: login, own analysis/day detail, personal sample suppression`)
  }
  const [leader, member] = sessions
  const team = await api(teamPath + teamQuery, leader)
  const memberTeam = await api(teamPath + teamQuery, member)
  assert.deepEqual(memberTeam.metrics, team.metrics, 'Members may read the same aggregate metrics')
  assert.equal(conversations.size, 24)
  assert.equal(team.coverage.selectedCount, 24)
  assert.equal(metric(team, 'confirmedSuccessRate').value, 0.75)
  assert.equal(metric(team, 'errorReworkRate').value, 0.25)
  const tokenMetric = metric(team, 'tokensPerConfirmedSuccess')
  assert.equal(tokenMetric.numerator, tokens, 'Aggregate tokens must equal the 24 unique conversations')
  assert.equal(tokenMetric.denominator, 18)
  assert.equal(tokenMetric.value, Math.round(tokens / 18 * 100) / 100)
  checks.push('team: 24 unique conversations, 75% success, 25% error rework, exact token sum')

  await api(`/feedbacks/${seed.memberFeedbackId}`, member)
  const privateFeedback = await request(`/feedbacks/${seed.memberFeedbackId}`, leader)
  assert.equal(privateFeedback.status, 404, 'Team leader must not read member private feedback')
  assert.equal(privateFeedback.result.code, 'RESOURCE_NOT_FOUND')
  const privateAnalysis = await request(`/analyses/${seed.analyses[1]}/items?page=1&size=100`, leader)
  assert.equal(privateAnalysis.status, 404, 'Team leader must not read member analysis items')
  checks.push('privacy: leader denied member private feedback and analysis')

  const settings = await api(teamPath + '/settings', leader)
  const { taskTypeId, ...taskType } = settings.taskType
  const denied = await request(teamPath + '/settings', member, {
    expectedVersion: settings.version, taskType, targetFrom: settings.targetFrom,
    targetTo: settings.targetTo, onboardingStartDate: settings.onboardingStartDate,
    targetCount: settings.targetCount,
  }, 'PUT')
  assert.equal(denied.status, 403)
  assert.equal(denied.result.code, 'ROLE_FORBIDDEN')
  assert.equal((await api(teamPath + '/settings', leader)).version, settings.version)
  const guides = await api(teamPath + '/guides?page=1&size=100', member)
  assert.ok(guides.items.length > 0)
  checks.push('permissions: member settings write denied; shared guide readable')

  const report = { checkedAt: new Date().toISOString(), teamId: seed.teamId, checks,
    uniqueConversations: conversations.size, estimatedTokens: tokens,
    successRate: 0.75, errorReworkRate: 0.25, tokensPerSuccess: tokenMetric.value,
    limitations: ['4 personal items < minimum sample 20', 'One populated day/week only',
      'External LLM responses are synthetic; live AI quality and provider billing are not verified'] }
  await writeFile('demo-fixtures/local-demo-verification.json', JSON.stringify(report, null, 2) + '\n')
  console.log(JSON.stringify(report, null, 2))
} finally {
  await Promise.allSettled(sessions.map(token => api('/auth/logout', token, {})))
}
