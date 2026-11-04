import {defineConfig} from '@playwright/test'
export default defineConfig({
 testDir:'./tests',testMatch:['continuous-batches.spec.ts','multi-warehouse-outbound.spec.ts','warehouse-flow.spec.ts','warehouse-handover-recovery.spec.ts','warehouse-handover-recovery-ui.spec.ts','work-order-report-summary-race.spec.ts','configurable-measurement-dialogs.spec.ts'],workers:1,timeout:30000,
 use:{baseURL:'http://127.0.0.1:5216',launchOptions:{channel:'chrome'}},
 webServer:{command:'node node_modules/vite/bin/vite.js --port 5216 --strictPort',url:'http://127.0.0.1:5216',reuseExistingServer:false},
 reporter:[['list'],['json',{outputFile:process.env.CONTINUOUS_BATCH_REPORT||'../.runtime/continuous-batches-20261104/frontend-ui.json'}]],outputDir:process.env.CONTINUOUS_BATCH_ARTIFACTS||'../.runtime/continuous-batches-20261104/frontend-ui-artifacts'
})
