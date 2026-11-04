import {defineConfig} from '@playwright/test'
export default defineConfig({
 testDir:'./tests',testMatch:['login-home.spec.ts','dashboard-permission.spec.ts','multi-warehouse-inbound.spec.ts','multi-warehouse-outbound.spec.ts','mobile-navigation.spec.ts'],timeout:30000,workers:1,
 use:{baseURL:'http://127.0.0.1:5218',viewport:{width:1440,height:1000},launchOptions:{channel:'chrome'},screenshot:'only-on-failure'},
 webServer:{command:'node node_modules/vite/bin/vite.js --port 5218 --strictPort',url:'http://127.0.0.1:5218',reuseExistingServer:false},
 outputDir:'../.runtime/login-inbound-people-verification/playwright',reporter:[['list'],['json',{outputFile:'../.runtime/login-inbound-people-verification/playwright.json'}]]
})
