import {defineConfig} from '@playwright/test'
export default defineConfig({
 testDir:'./tests',testMatch:['inventory-measurement-configurable.spec.ts','inventory-raw-stage-quantity.spec.ts','stock-availability.spec.ts','inventory-adjustment.spec.ts','raw-inventory-customers.spec.ts'],workers:1,timeout:20000,
 use:{baseURL:'http://127.0.0.1:5197',launchOptions:{channel:'chrome'}},
 webServer:{command:'node node_modules/vite/bin/vite.js --port 5197 --strictPort',url:'http://127.0.0.1:5197',reuseExistingServer:false},
 reporter:[['list'],['json',{outputFile:process.env.INVENTORY_MEASUREMENT_REPORT||'../.runtime/configurable-measurement-20261104/inventory-ui-latest.json'}]],outputDir:'.runtime/inventory-measurement-results'
})
