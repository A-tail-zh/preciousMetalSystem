import {randomUUID} from 'node:crypto'
process.env.TEST_RUN_ID ||= randomUUID()
import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './tests',
  testIgnore:'**/bulk-data-live.spec.ts',
  globalTeardown: './tests/global-teardown.ts',
  timeout: 30000,
  workers: 1,
  use: { baseURL: 'http://127.0.0.1:5173', viewport: {width:1141,height:801}, launchOptions: {channel: 'chrome'}, screenshot: 'only-on-failure' },
  webServer: {command: 'node node_modules/vite/bin/vite.js --port 5173',url: 'http://127.0.0.1:5173', reuseExistingServer: true},
  reporter: [['list']]
})
