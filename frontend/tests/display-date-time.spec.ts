import {test,expect} from '@playwright/test';
import {displayDateTime,dateTimeCell} from '../src/utils/display-date-time';
test('date time display preserves wall time, date-only values and empty placeholders',()=>{
 for(const value of ['2026-10-19T10:48:48','2026-10-19 10:48:48','2026-10-19T10:48:48.123456'])expect(displayDateTime(value)).toBe('2026-10-19 10:48:48');
 expect(displayDateTime('2026-10-19T10:48')).toBe('2026-10-19 10:48:00');
 expect(displayDateTime('2026-10-19')).toBe('2026-10-19');
 for(const value of [null,undefined,'',' '])expect(displayDateTime(value)).toBe('—');
 expect(displayDateTime('unrecognized')).toBe('unrecognized');expect(displayDateTime(10)).toBe('10');
 expect(displayDateTime('2026-10-19T02:48:48Z')).toBe('2026-10-19T02:48:48Z');
 expect(dateTimeCell({}, {}, '2026-10-19T10:48:48')).toBe('2026-10-19 10:48:48');
});

test('factory and product tables display ISO local timestamps as readable dates',async({page})=>{
 await page.context().addCookies([{name:'Admin-Token',value:'time-display-fixture',url:'http://127.0.0.1:5173'}]);
 await page.route('**/dev-api/**',async route=>{const path=new URL(route.request().url()).pathname.replace('/dev-api','');let response:any={code:200,data:[],rows:[],total:0};
 if(path==='/getInfo')response={code:200,user:{userId:1,userName:'admin',nickName:'管理员'},roles:['admin'],permissions:['*:*:*']};
 else if(path==='/getRouters')response.data=[{path:'/metal',component:'Layout',children:['factory','product'].map(p=>({path:p,component:'metal/'+p+'/index',name:p,meta:{title:p,noCache:true}}))}];
 else if(path==='/metal/factory/list')response={code:200,rows:[{factoryId:'43',factoryName:'时间展示测试工厂',area:'1',address:'测试地址',productionLineNames:[],updateTime:'2026-10-19T10:48:48'}],total:1};
 else if(path==='/metal/product/list')response={code:200,rows:[{id:'1',code:'TIME-001',name:'时间展示测试产品',category:'铱杆实心',createTime:'2026-10-19T10:48:48',specification:'直径1cm；长度20cm'}],total:1};
 await route.fulfill({json:response});});
 for(const path of ['/metal/factory','/metal/product']){await page.goto(path);await expect(page.getByRole('cell',{name:'2026-10-19 10:48:48',exact:true})).toBeVisible();await expect(page.getByText('2026-10-19T10:48:48',{exact:true})).toHaveCount(0);}
 await page.screenshot({path:'../.runtime/bug-audit/time-display.png',fullPage:true});
});
