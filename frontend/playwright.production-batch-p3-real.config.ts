import {defineConfig} from '@playwright/test'
export default defineConfig({
 testDir:'./tests',testMatch:'production-batch-p3-real.spec.ts',workers:1,timeout:60000,
 use:{baseURL:'http://127.0.0.1:5266',viewport:{width:1600,height:1100},launchOptions:{channel:'chrome'},screenshot:'only-on-failure'},
 outputDir:'../.runtime/production-batch-p3-20261112/real-ui-results',reporter:[['list'],['json',{outputFile:'../.runtime/production-batch-p3-20261112/playwright-real-results.json'}]]
})
