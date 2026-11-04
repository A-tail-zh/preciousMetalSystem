import {defineConfig} from '@playwright/test'
import affected from './playwright.unified-regression.config'
export default defineConfig({...affected,
 testMatch:(affected.testMatch as string[]).filter(name=>name!=='master-crud.spec.ts'),
 use:{...affected.use,baseURL:'http://127.0.0.1:5201',proxy:{server:'http://127.0.0.1:5202'},launchOptions:{channel:'chrome',args:['--proxy-bypass-list=<-loopback>']}},
 outputDir:'../.runtime/configurable-measurement-20261104/frontend-regression-isolated',
 reporter:[['list'],['json',{outputFile:'../.runtime/configurable-measurement-20261104/frontend-regression-isolated.json'}]]
})
