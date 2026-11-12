import {defineConfig} from '@playwright/test'
export default defineConfig({testDir:'./tests',testMatch:['storage-lan.spec.ts','storage-live.spec.ts','review-values.spec.ts'],workers:1,timeout:30000,use:{baseURL:'http://127.0.0.1:5211',viewport:{width:1440,height:1000},launchOptions:{channel:'chrome'}},outputDir:'../.runtime/three-domain-audit-20261110/processing/ui-results',reporter:[['list']]})
