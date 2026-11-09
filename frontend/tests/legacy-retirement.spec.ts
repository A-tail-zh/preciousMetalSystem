import {test, expect} from '@playwright/test'
import {businessNavigation} from '../src/utils/business-navigation'
import {isRetiredBusinessRoute} from '../src/utils/retired-business'

test('retired navigation is absent for administrators and ordinary accounts', () => {
  for (const permissions of [['*:*:*'], ['metal:work-order:list', 'metal:warehouse:list', 'metal:warehouse-order:add', 'metal:inventory:list', 'metal:product:list']]) {
    const entries = businessNavigation({id: 1, sessionReady: true, permissions}).flatMap(group => [...group.items, ...(group.entry ? [group.entry] : [])])
    expect(entries.some(item => isRetiredBusinessRoute(item.to))).toBe(false)
    expect(entries.some(item => item.to === '/metal/product')).toBe(true)
  }
})

test('dynamic routes are excluded by full path or backend component name', () => {
  for (const name of ['work-order', 'warehouse', 'warehouse-order', 'inventory', 'acid-wash-history']) {
    expect(isRetiredBusinessRoute(`/metal/${name}`)).toBe(true)
    expect(isRetiredBusinessRoute(name, `metal/${name}/index`)).toBe(true)
  }
  for (const name of ['product', 'factory', 'quotation', 'work-order-new']) {
    expect(isRetiredBusinessRoute(`/metal/${name}`, `metal/${name}/index`)).toBe(false)
  }
})
