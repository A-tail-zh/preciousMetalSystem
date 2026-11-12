import {defineConfig} from '@playwright/test'
import regression from './playwright.production-batch-p4.config'

export default defineConfig({
 ...regression,
 outputDir:'../.runtime/production-batch-legacy-adoption-20261112/ui-results',
 reporter:[['list'],['json',{outputFile:'../.runtime/production-batch-legacy-adoption-20261112/playwright-results.json'}]]
})
