import {defineConfig} from '@playwright/test'
export default defineConfig({
 testDir:'./tests',testMatch:['inventory-detail-refresh.spec.ts','work-order-empty-sections.spec.ts','work-order-list-session.spec.ts'],timeout:30000,workers:1,
 use:{baseURL:'http://127.0.0.1:5199',viewport:{width:1141,height:801},launchOptions:{channel:'chrome'},screenshot:'only-on-failure'},
 webServer:{command:'node node_modules/vite/bin/vite.js --port 5199 --strictPort',url:'http://127.0.0.1:5199',reuseExistingServer:false},
 reporter:[['list'],['json',{outputFile:'../.runtime/unified-production-20261103/reg-2-4-refresh.json'}]],
 outputDir:'../.runtime/unified-production-20261103/reg-2-4-refresh'
})
