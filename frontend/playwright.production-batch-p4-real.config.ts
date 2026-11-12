import {defineConfig} from '@playwright/test'
export default defineConfig({
 testDir:'./tests',testMatch:'production-batch-p4-real.spec.ts',workers:1,timeout:60000,
 use:{baseURL:process.env.P4_REAL_FRONTEND_URL||'http://127.0.0.1:5268',viewport:{width:1600,height:1100},launchOptions:{channel:'chrome'},screenshot:'only-on-failure'},
 outputDir:'../.runtime/production-batch-p4-20261112/real-ui-results',reporter:[['list'],['json',{outputFile:'../.runtime/production-batch-p4-20261112/playwright-real-results.json'}]]
})
