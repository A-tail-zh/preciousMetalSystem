import {test, expect} from '@playwright/test'

for (const fallback of [false, true]) {
  test('UUID v4 with ' + (fallback ? 'HTTP-compatible fallback' : 'native browser API'), async ({page}) => {
    if (fallback) await page.addInitScript(() => {
      Object.defineProperty(globalThis.crypto, 'randomUUID', {value: undefined, configurable: true})
    })
    await page.goto('/login')
    const result = await page.evaluate(async () => {
      const modulePath = '/src/utils/uuid.ts'
      const {createUUID} = await import(modulePath)
      const ids = Array.from({length: 1000}, () => createUUID())
      return {native: typeof crypto.randomUUID === 'function', unique: new Set(ids).size,
        valid: ids.every(id => /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(id))}
    })
    expect(result.native).toBe(!fallback)
    expect(result.unique).toBe(1000)
    expect(result.valid).toBe(true)
  })
}
