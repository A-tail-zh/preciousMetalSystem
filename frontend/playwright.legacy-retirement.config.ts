import {defineConfig} from '@playwright/test'
export default defineConfig({
  testDir: './tests', testMatch: 'legacy-retirement.spec.ts', workers: 1, reporter: 'list'
})
