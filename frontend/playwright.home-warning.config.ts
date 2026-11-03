import {defineConfig} from '@playwright/test'
export default defineConfig({
 testDir:'./tests',testMatch:'home-warning-units.spec.ts',timeout:20000,workers:1,
 use:{baseURL:'http://127.0.0.1:5200',viewport:{width:1440,height:1000},launchOptions:{channel:'chrome'}},
 webServer:{command:'node node_modules/vite/bin/vite.js --port 5200 --strictPort',url:'http://127.0.0.1:5200',reuseExistingServer:false},
 reporter:[['list'],['json',{outputFile:'../.runtime/unified-production-20261103/r1-home-warning-units.json'}]],
 outputDir:'../.runtime/unified-production-20261103/r1-home-warning-units'
})
