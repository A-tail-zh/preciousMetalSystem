import {defineConfig} from '@playwright/test'
export default defineConfig({testDir:'./tests',testMatch:['catalog-configuration.spec.ts','review-values.spec.ts','storage-lan.spec.ts'],workers:1,timeout:30000,use:{baseURL:'http://127.0.0.1:5206',viewport:{width:1440,height:1000},launchOptions:{channel:'chrome'}},outputDir:'../.runtime/catalog-config-ui-20261110/ui-results',reporter:[['list']]})
