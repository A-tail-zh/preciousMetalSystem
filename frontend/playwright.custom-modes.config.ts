import {defineConfig} from '@playwright/test'
const baseURL=process.env.STORAGE_TEST_BASE_URL||'http://127.0.0.1:5232'
export default defineConfig({
 testDir:'./tests',testMatch:['material-debt-live.spec.ts','storage-live.spec.ts'],workers:1,timeout:30000,
 use:{baseURL,viewport:{width:1440,height:1000},launchOptions:{channel:'chrome'},screenshot:'only-on-failure'},
 outputDir:'../.runtime/custom-modes-20261110/ui-results',reporter:[['list']]
})
