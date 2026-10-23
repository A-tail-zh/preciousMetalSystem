import {defineConfig}from'@playwright/test';import base from './playwright.config';
export default defineConfig({...base,testIgnore:[],testMatch:'**/bulk-data-live.spec.ts',outputDir:'test-results-bulk',use:{...base.use,baseURL:'http://127.0.0.1:15173'},webServer:{command:'node node_modules/vite/bin/vite.js --port 15173 --strictPort',url:'http://127.0.0.1:15173',reuseExistingServer:false}})
