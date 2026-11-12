import {defineConfig} from '@playwright/test'
export default defineConfig({testDir:'./tests',testMatch:['storage-live.spec.ts','storage-lan.spec.ts'],workers:1,timeout:30000,use:{baseURL:process.env.STORAGE_TEST_BASE_URL||'http://127.0.0.1:5202',viewport:{width:1440,height:1000},launchOptions:{channel:'chrome'}},outputDir:'../.runtime/storage-live-20261109/ui-results',reporter:[['list']]})

