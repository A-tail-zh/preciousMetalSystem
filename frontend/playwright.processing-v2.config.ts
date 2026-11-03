import {defineConfig} from '@playwright/test'
export default defineConfig({
 testDir:'./tests',testMatch:'processing-v2.spec.ts',timeout:30000,workers:1,
 use:{baseURL:'http://127.0.0.1:5187',viewport:{width:1440,height:1000},launchOptions:{channel:'chrome'},screenshot:'only-on-failure'},
 webServer:{command:'node node_modules/vite/bin/vite.js --port 5187 --strictPort',url:'http://127.0.0.1:5187',reuseExistingServer:false},
 outputDir:'../.runtime/processing-v2-20261103/playwright',reporter:[['list']]
})
