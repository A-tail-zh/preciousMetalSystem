import {defineConfig} from '@playwright/test'
import regression from './playwright.production-batch-p4.config'
export default defineConfig({...regression,testMatch:[...(regression.testMatch as string[]),'production-out-auto-batch.spec.ts'],outputDir:'../.runtime/production-batch-plan-limit-20261112/ui-results',reporter:[['list'],['json',{outputFile:'../.runtime/production-batch-plan-limit-20261112/playwright-results.json'}]]})
