import {defineConfig} from '@playwright/test'
export default defineConfig({
 testDir:'./tests',testMatch:['dashboard-permission.spec.ts','home-warning-units.spec.ts','role-permission-tree.spec.ts','work-order-list-session.spec.ts'],grepInvert:/live role options/,timeout:30000,workers:1,
 use:{baseURL:'http://127.0.0.1:5218',viewport:{width:1440,height:900},launchOptions:{channel:'chrome'},screenshot:'only-on-failure'},
 webServer:{command:'node node_modules/vite/bin/vite.js --port 5218 --strictPort',url:'http://127.0.0.1:5218',reuseExistingServer:false},
 outputDir:'../.runtime/dashboard-permission-verification/playwright',reporter:[['list']]
})
