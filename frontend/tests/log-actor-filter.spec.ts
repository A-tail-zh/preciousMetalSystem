import {test,expect} from '@playwright/test';
test('operation logs search people labels, apply IDs, preserve selection and reset on factory change',async({page})=>{
 const calls:{path:string;params:URLSearchParams}[]=[];await page.context().addCookies([{name:'Admin-Token',value:'log-actor-fixture',url:'http://127.0.0.1:5173'}]);
 await page.route('**/dev-api/**',async route=>{const url=new URL(route.request().url()),path=url.pathname.replace('/dev-api','');calls.push({path,params:url.searchParams});let response:any={code:200,data:[],rows:[],total:0};
 if(path==='/getInfo')response={code:200,user:{userId:1,userName:'admin',nickName:'管理员'},roles:['admin'],permissions:['*:*:*']};
 else if(path==='/getRouters')response.data=[{path:'/audit',component:'Layout',children:[{path:'operations',component:'audit/operations',name:'Operations'}]}];
 else if(path==='/monitor/operlog/actors')response={code:200,rows:url.searchParams.get('keyword')==='无人'?[]:[{id:'165',name:'工厂管理员｜张三｜13800000000'}],hasMore:false,factories:[{id:'43',name:'西安测试工厂'},{id:'44',name:'宝鸡测试工厂'}]};
 await route.fulfill({json:response});});
 await page.goto('/audit/operations');const actor=page.getByRole('combobox',{name:'操作人员',exact:true});await actor.click();await actor.fill('张');await expect(page.getByRole('option',{name:'工厂管理员｜张三｜13800000000',exact:true})).toBeVisible();await page.getByRole('option',{name:'工厂管理员｜张三｜13800000000',exact:true}).click();await page.locator('.log-toolbar').getByRole('button',{name:'查询',exact:true}).click();await expect.poll(()=>calls.filter(c=>c.path==='/monitor/operlog/list').at(-1)?.params.get('actorId')).toBe('165');expect(calls.some(c=>c.path==='/monitor/operlog/actors'&&c.params.get('keyword')==='张')).toBe(true);
 await actor.click();await actor.fill('无人');await expect(page.getByRole('option',{name:'工厂管理员｜张三｜13800000000',exact:true})).toBeVisible();await page.keyboard.press('Escape');
 const factory=page.locator('.log-toolbar .filters>.el-select').first();await factory.locator('.el-select__wrapper').click();await page.getByRole('option',{name:'宝鸡测试工厂',exact:true}).click();await expect(actor).toHaveValue('');await page.locator('.log-toolbar').getByRole('button',{name:'查询',exact:true}).click();await expect.poll(()=>calls.filter(c=>c.path==='/monitor/operlog/list').at(-1)?.params.get('factoryId')).toBe('44');expect(calls.filter(c=>c.path==='/monitor/operlog/list').at(-1)?.params.has('actorId')).toBe(false);
 await page.locator('.log-toolbar').getByRole('button',{name:'重置',exact:true}).click();await expect.poll(()=>calls.filter(c=>c.path==='/monitor/operlog/list').at(-1)?.params.has('factoryId')).toBe(false);
});

test('login logs select a person, export applied ID and reset without changing stored account display',async({page})=>{
 const calls:{path:string;params:URLSearchParams;body:URLSearchParams}[]=[];await page.context().addCookies([{name:'Admin-Token',value:'login-person-fixture',url:'http://127.0.0.1:5173'}]);
 await page.route('**/dev-api/**',async route=>{const req=route.request(),url=new URL(req.url()),path=url.pathname.replace('/dev-api','');calls.push({path,params:url.searchParams,body:new URLSearchParams(req.postData()??'')});let response:any={code:200,data:[],rows:[],total:0};
 if(path==='/getInfo')response={code:200,user:{userId:1,userName:'admin',nickName:'管理员'},roles:['admin'],permissions:['*:*:*']};
 else if(path==='/getRouters')response.data=[{path:'/audit',component:'Layout',children:[{path:'logins',component:'audit/logins',name:'Logins'}]}];
 else if(path==='/monitor/logininfor/actors')response={code:200,rows:[{id:'165',name:'工厂管理员｜张三｜13800000000'}],hasMore:false,factories:[]};
 else if(path==='/monitor/logininfor/list')response={code:200,rows:[{userName:'138****0000',loginTime:'2026-10-19 11:00:00',status:'0',msg:'登录成功'}],total:1};
 else if(path==='/monitor/logininfor/export'){await route.fulfill({contentType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',body:Buffer.from('PK mock fixture')});return;}
 await route.fulfill({json:response});});
 await page.goto('/audit/logins');const actor=page.getByRole('combobox',{name:'登录人员',exact:true});await actor.click();await actor.fill('138');await expect.poll(()=>calls.some(c=>c.path==='/monitor/logininfor/actors'&&c.params.get('keyword')==='138')).toBe(true);await page.getByRole('option',{name:'工厂管理员｜张三｜13800000000',exact:true}).click();await page.locator('.log-toolbar').getByRole('button',{name:'查询',exact:true}).click();await expect.poll(()=>calls.filter(c=>c.path==='/monitor/logininfor/list').at(-1)?.params.get('actorId')).toBe('165');await expect(page.getByRole('cell',{name:'138****0000',exact:true})).toBeVisible();
 await page.locator('.log-toolbar').getByRole('button',{name:'导出',exact:true}).click();await expect.poll(()=>calls.find(c=>c.path==='/monitor/logininfor/export')?.body.get('actorId')).toBe('165');
 await page.locator('.log-toolbar').getByRole('button',{name:'重置',exact:true}).click();await expect.poll(()=>calls.filter(c=>c.path==='/monitor/logininfor/list').at(-1)?.params.has('actorId')).toBe(false);await expect(actor).toHaveValue('');
});
