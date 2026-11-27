import { mkdir, writeFile } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import assert from 'node:assert/strict'

// This script has no configurable host: never seed the deployed backend.
const base = 'http://127.0.0.1:8081/api/v1'
const password = 'DemoOnly1234!'
const marker = await fetch(base + '/users/me')
assert.equal(marker.headers.get('X-Toki-Demo'), 'local-synthetic', 'Refusing to seed a server without the demo profile marker')
async function api(path, token, body, method = body == null ? 'GET' : 'POST', headers = {}) {
  const response = await fetch(base + path, {
    method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body && !(body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}), ...headers },
    body: body == null ? undefined : body instanceof FormData ? body : JSON.stringify(body),
  })
  const text = await response.text()
  const result = text ? JSON.parse(text) : {}
  if (!response.ok) throw Object.assign(new Error(`${method} ${path}: ${response.status} ${result.code}`), { code: result.code, status: response.status })
  return result.data
}
function conversation(user, index) {
  const time = Date.parse('2026-11-18T03:00:00Z') / 1000 + index * 60
  return { id: `demo-${user}-${index}`, title: `[DEMO] 고객 안내 ${index + 1}`, create_time: time, current_node: 'a', mapping: {
    u: { parent: null, children: ['a'], message: { author: { role: 'user' }, create_time: time,
      content: { content_type: 'text', parts: ['[DEMO] 가상의 카페 고객에게 예약 안내를 정중한 한 문장으로 작성해줘.'] }, metadata: {} } },
    a: { parent: 'u', children: [], message: { author: { role: 'assistant' }, create_time: time + 1,
      content: { content_type: 'text', parts: ['[DEMO] 예약하신 시간에 방문해 주시면 감사하겠습니다.'] }, metadata: { model_slug: 'gpt-4o' } } },
  } }
}
async function upload(teamId, token, rows) {
  const form = new FormData()
  form.set('settingsVersion', '1')
  form.set('file', new Blob([JSON.stringify(rows)], { type: 'application/json' }), 'conversations.json')
  return api(`/teams/${teamId}/uploads`, token, form)
}
const users = []
for (let index = 0; index < 6; index++) {
  const email = index ? `demo.member${index}@local.test` : 'demo.leader@local.test'
  try { await api('/auth/signup', null, { name: index ? `시연 팀원 ${index}` : '시연 팀장', email, password }) }
  catch (error) { if (error.code !== 'EMAIL_IN_USE') throw error }
  const login = await api('/auth/login', null, { email, password })
  assert.ok(login.accessToken, 'Local login must issue an access token')
  users.push({ index, email, token: login.accessToken })
}
const leader = users[0]
const team = await api('/teams', leader.token, { name: `로컬 합성 시연 ${new Date().toISOString().slice(0, 19)}`, description: 'demo 프로필 전용 합성 데이터 · 실제 업무 성과 아님' })
const teamId = team.teamId
for (const user of users.slice(1)) await api(`/teams/${teamId}/members`, leader.token, { email: user.email })
const settings = await api(`/teams/${teamId}/settings`, leader.token, {
  expectedVersion: 0, taskType: { name: '고객 안내 문장 작성', successConditions: ['고객 안내의 의미를 유지한다'], failureConditions: ['안내 조건을 잘못 전달한다'], unknownConditions: ['실제 사용 결과를 확인할 수 없다'] },
  targetFrom: '2026-11-01', targetTo: '2026-11-30', onboardingStartDate: '2026-11-16', targetCount: 24,
}, 'PUT')
await api(`/teams/${teamId}/goals`, leader.token, { expectedVersion: 0, successRateMin: 0.75, errorReworkRateMax: 0.25, tokensPerSuccessMax: 2000 }, 'PUT')
await mkdir('demo-fixtures', { recursive: true })
const evidence = { teamId, analyses: [], feedbackIds: [], uniqueItems: 0, model: 'demo-local' }
for (const user of users) {
  const rows = Array.from({ length: 4 }, (_, index) => conversation(user.index, index))
  const repeatedRows = [...rows, rows[0]]
  if (user.index === 1) {
    await writeFile('demo-fixtures/conversations-duplicate.json', JSON.stringify(repeatedRows, null, 2))
    const conflict = structuredClone(rows[0])
    conflict.mapping.u.message.content.parts = ['[DEMO] 같은 ID에 다른 내용']
    await writeFile('demo-fixtures/conversations-conflict.json', JSON.stringify([rows[0], conflict], null, 2))
    try { await upload(teamId, user.token, [rows[0], conflict]); assert.fail('Conflicting IDs must be rejected') }
    catch (error) { assert.equal(error.code, 'CONFLICTING_CONVERSATION_ID') }
  }
  const uploaded = await upload(teamId, user.token, repeatedRows)
  assert.equal(uploaded.conversationCount, 5)
  assert.equal(uploaded.eligibleConversationCount, 4)
  const candidates = await api(`/uploads/${uploaded.uploadId}/conversations?page=1&size=100`, user.token)
  assert.equal(candidates.items.length, 4)
  assert.equal(new Set(candidates.items.map(item => item.externalId)).size, 4)
  const started = await api(`/uploads/${uploaded.uploadId}/analyses`, user.token, {
    settingsVersion: settings.version, selections: candidates.items.map(item => ({ selectionId: item.selectionId, taskTypeId: settings.taskType.taskTypeId })),
  }, 'POST', { 'Idempotency-Key': randomUUID() })
  let status
  for (let count = 0; count < 90; count++) {
    status = await api(`/analyses/${started.analysisId}`, user.token)
    if (['SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED'].includes(status.status)) break
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  assert.equal(status.status, 'SUCCEEDED', JSON.stringify(status))
  assert.equal(status.selectedCount, 4)
  const items = await api(`/analyses/${started.analysisId}/items?page=1&size=100`, user.token)
  assert.equal(items.total, 4)
  for (const [index, item] of items.items.entries()) {
    assert.equal(item.status, 'SUCCEEDED')
    await api(`/analysis-items/${item.itemId}/outcome`, user.token, { expectedVersion: 0, outcome: index < 3 ? 'SUCCESS' : 'FAILURE' }, 'PUT')
    await api(`/analysis-items/${item.itemId}/rework-assessment`, user.token, { expectedVersion: 0, type: index ? 'NONE' : 'ERROR_CORRECTION' }, 'PUT')
    evidence.feedbackIds.push(...item.feedbackIds)
  }
  evidence.uniqueItems += items.total
  evidence.analyses.push(started.analysisId)
  if (user.index === 1) {
    const feedbackId = items.items[0].feedbackIds[0]
    assert.ok(feedbackId)
    const preview = await api(`/feedbacks/${feedbackId}/share-preview`, user.token, {})
    assert.equal(preview.redactionStatus, 'PASSED')
    const shared = await api(`/feedbacks/${feedbackId}/share`, user.token, { shared: true, expectedShareVersion: 0, previewId: preview.previewId, previewVersion: preview.previewVersion }, 'PATCH')
    await api(`/teams/${teamId}/guides/${shared.sharedCaseId}`, leader.token, { expectedShareVersion: shared.shareVersion }, 'PUT')
    evidence.memberFeedbackId = feedbackId
    const repeated = await upload(teamId, user.token, rows)
    const repeatedCandidates = await api(`/uploads/${repeated.uploadId}/conversations?page=1&size=100`, user.token)
    assert.ok(repeatedCandidates.items.every(item => !item.selectable && item.duplicateType === 'SAME_CONTENT'))
  }
  console.log(`Local member ${user.index}: 5 input rows -> 4 analyzed items (no duplicate aggregation)`)
}
const kpis = await api(`/teams/${teamId}/dashboard/kpis?scope=TEAM&settingsVersion=1&weekStart=2026-11-16`, leader.token)
assert.equal(kpis.coverage.selectedCount, 24, 'Duplicate rows must not increase the cohort')
assert.equal(kpis.metrics.find(metric => metric.code === 'confirmedSuccessRate').value, 0.75)
assert.equal(kpis.metrics.find(metric => metric.code === 'errorReworkRate').value, 0.25)
const guides = await api(`/teams/${teamId}/guides?page=1&size=100`, users[1].token)
assert.ok(guides.items.length > 0)
evidence.kpis = kpis
await writeFile('demo-fixtures/local-demo-result.json', JSON.stringify(evidence, null, 2))
console.log(JSON.stringify({ teamId, uniqueItems: evidence.uniqueItems, guideCount: guides.items.length, leader: leader.email, member: users[1].email }))
for (const user of users) await api('/auth/logout', user.token, {})
