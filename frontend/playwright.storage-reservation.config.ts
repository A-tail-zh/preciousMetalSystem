import {defineConfig} from '@playwright/test'
export default defineConfig({testDir:'./tests',testMatch:'storage-reservation.spec.ts',workers:1,timeout:20000,use:{baseURL:process.env.STORAGE_TEST_BASE_URL||'http://127.0.0.1:5291',viewport:{width:1440,height:1000},launchOptions:{channel:'chrome'}},outputDir:'../.runtime/storage-reservation-20261111/frontend/ui-results',reporter:[['list']]})
