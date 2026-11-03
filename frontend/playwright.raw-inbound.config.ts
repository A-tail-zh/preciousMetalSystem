import {defineConfig} from '@playwright/test'
export default defineConfig({
 testDir:'./tests',testMatch:['raw-inbound-acceptance.spec.ts','warehouse-seven-review.spec.ts'],timeout:30000,workers:1,
 use:{baseURL:'http://127.0.0.1:5192',viewport:{width:1440,height:1000},launchOptions:{channel:'chrome'},screenshot:'only-on-failure'},
 webServer:{command:'node node_modules/vite/bin/vite.js --port 5192 --strictPort',url:'http://127.0.0.1:5192',reuseExistingServer:false},
 outputDir:'../.runtime/raw-inbound-acceptance-20261103',reporter:[['list']]
})
