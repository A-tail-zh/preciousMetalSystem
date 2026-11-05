import {test as base} from '@playwright/test'
import {assertRegisteredIsolation} from '../../../tools/isolated-test-environment.mjs'
export * from '@playwright/test'

export const test=base.extend<{isolationNetwork:void}>({
 isolationNetwork:[async({context},use)=>{
  if(process.env.TEST_ISOLATED_RUN==='1'){
   const origins=assertRegisteredIsolation()
   await context.route('**/*',async route=>{
    const url=new URL(route.request().url())
    if(['http:','https:'].includes(url.protocol)&&!origins.has(url.origin)){
     await route.abort('blockedbyclient')
     throw Error('Isolated browser refused network target: '+url.origin+url.pathname)
    }
    await route.fallback()
   })
  }
  await use()
 },{auto:true}]
})
