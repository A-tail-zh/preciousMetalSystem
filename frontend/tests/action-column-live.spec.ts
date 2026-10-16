import {test,expect} from '@playwright/test'
import {login,api,ok} from '../../tools/local-test-client.mjs'
test('操作列按当前账号权限显示',async({browser})=>{
 test.skip(process.env.LIVE_ACTION_COLUMN!=='1','需要本地服务');
 for(const account of ['19900001002','admin']){
  const token=await login(account,'12345678'),context=await browser.newContext();const permissions=ok(await api('/getInfo',token)).permissions as string[];const can=(p:string)=>permissions.includes('*:*:*')||permissions.includes(p);
  try{await context.addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}]);const page=await context.newPage();await page.goto('/metal/factory');await expect(page.locator('.el-table__body-wrapper .el-table__row').first()).toBeVisible();const nav=page.getByRole('navigation',{name:'主导航'});const groups=[['基础信息管理',['metal:factory:list','metal:product:list','metal:production-line:list','metal:material:list','metal:process:list']],['报价管理',['metal:quote-rule:list','metal:quotation:list']],['仓储管理',['metal:inventory:list','metal:warehouse-order:list','metal:warehouse:list']],['用户管理',['system:user:list','system:role:list']],['预警管理',['metal:warning-record:list']]] as const;for(const [title,perms] of groups)await expect(nav.getByRole('button',{name:title,exact:false})).toHaveCount(perms.some(can)?1:0);await expect(nav.getByRole('link',{name:'工单管理',exact:true})).toHaveCount(can('metal:work-order:list')?1:0);const header=page.locator('.el-table__header-wrapper th').filter({hasText:'操作'});if(account==='admin')await expect(header).toHaveCount(1);else await expect(header).toHaveCount(0);const selections=page.locator('.el-table__header-wrapper .el-checkbox');if(account==='admin')await expect(selections).toHaveCount(1);else{await expect(selections).toHaveCount(0);await expect(page.locator('.el-table__body-wrapper .el-checkbox')).toHaveCount(0);}
  }finally{await context.close()}
 }
})
