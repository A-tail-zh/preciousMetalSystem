import {defineConfig} from '@playwright/test'
import regression from './playwright.production-batch-p4.config'
export default defineConfig({...regression,testMatch:['production-out-auto-batch.spec.ts','permission-ui.spec.ts','partial-acid-flow.spec.ts'],outputDir:'../.runtime/automatic-batch-flow-20261112/ui-results',reporter:[['list'],['json',{outputFile:'../.runtime/automatic-batch-flow-20261112/playwright-results.json'}]]})
