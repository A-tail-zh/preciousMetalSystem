import {defineConfig} from '@playwright/test'
export default defineConfig({
 testDir:'./tests',testMatch:['production-batch-p4.spec.ts','production-batch-p3.spec.ts','production-batch-p2.spec.ts','production-receipt-binding.spec.ts'],workers:1,timeout:30000,
 use:{baseURL:'http://127.0.0.1:5267',viewport:{width:1600,height:1100},launchOptions:{channel:'chrome'},screenshot:'only-on-failure'},
 webServer:{command:'node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5267 --strictPort',url:'http://127.0.0.1:5267',reuseExistingServer:false,env:{TEST_API_PROXY_TARGET:'http://127.0.0.1:1'}},
 outputDir:'../.runtime/production-batch-p4-20261112/ui-results',reporter:[['list'],['json',{outputFile:'../.runtime/production-batch-p4-20261112/playwright-results.json'}]]
})
