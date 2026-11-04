import {defineConfig} from '@playwright/test'
export default defineConfig({
 testDir:'./tests',testMatch:'visible-business-refresh-scheduler.spec.ts',timeout:20000,workers:1,
 use:{baseURL:'http://127.0.0.1:5194',launchOptions:{channel:'chrome'}},
 webServer:{command:'node node_modules/vite/bin/vite.js --port 5194 --strictPort',url:'http://127.0.0.1:5194',reuseExistingServer:false},
 reporter:[['list']],outputDir:'.runtime/refresh-scheduler-results'
})
