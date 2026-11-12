import {defineConfig} from '@playwright/test'
export default defineConfig({testDir:'./tests',testMatch:'permission-ui.spec.ts',workers:1,timeout:30000,use:{baseURL:process.env.PERMISSION_TEST_URL||'http://127.0.0.1:5202',viewport:{width:1600,height:1000},launchOptions:{channel:'chrome'}},outputDir:'../.runtime/permission-ui-20261110/ui-results',reporter:[['list']]})
