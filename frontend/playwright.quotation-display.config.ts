import {defineConfig} from '@playwright/test'
export default defineConfig({testDir:'./tests',testMatch:['quotation-display.spec.ts'],workers:1,timeout:30000,use:{baseURL:'http://127.0.0.1:5201',launchOptions:{channel:'chrome'}},outputDir:'../.runtime/quotation-display-20261104/ui-results',reporter:[['list'],['json',{outputFile:'../.runtime/quotation-display-20261104/ui-results.json'}]]})
