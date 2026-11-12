import {test,expect,type Page} from '@playwright/test'
import {signedAdjustment} from '../src/views/metal/storage-live/adjustment-display'
test('signed adjustment formatting preserves missing values and exact decimals',()=>{expect(signedAdjustment('100')).toBe('+100.00');expect(signedAdjustment('-0.10')).toBe('-0.10');expect(signedAdjustment('0')).toBe('0.00');expect(signedAdjustment(null)).toBe('—');expect(signedAdjustment('')).toBe('—');expect(signedAdjustment('2',true)).toBe('+2');expect(signedAdjustment('1.5',true)).toBe('—');expect(signedAdjustment('99999999999999.99')).toBe('+99999999999999.99')})
async function fixture(page:Page,baseURL:string,piece:boolean){
 const line={id:'41',stockId:'51',warehouseId:'1',category:'RAW',code:'001',name:'旧料',batchNo:'ST51',measurementMode:'WEIGHT',ownerId:'0',ownerName:'工厂自有',deltaWeight:'100',reason:'增加工厂料'}
 const second={...line,id:'42',measurementMode:'PIECE',ownerId:'8',ownerName:'客户甲',deltaWeight:'-2.50',deltaQuantity:'-1',reason:'盘点减少'}
 const doc={id:'31',factoryId:'101',number:'ADJ31',type:'ADJUSTMENT',kind:'ADJUST',status:'DRAFT',version:1,reviewRound:1,creatorId:'1',reviewerId:'9',warehouseIds:['1'],date:'2026-11-10',reason:'整单原因',canReview:true,lines:piece?[line,second]:[line]}
 await page.context().addCookies([{name:'Admin-Token',value:'mock',url:baseURL}]);await page.route('**/*-api/**',async route=>{const path=new URL(route.request().url()).pathname.replace(/^\/(prod|dev)-api/,'');let body:any={code:200,data:[],rows:[],total:0};
 if(path==='/getInfo')body={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']};
 if(path==='/metal/options/factories')body={code:200,data:[{id:'101',name:'本厂'}]};
 if(path==='/metal/storage-orders/list')body={code:200,rows:[doc],total:1};
 if(path==='/metal/storage-orders/31')body={code:200,data:doc};
 if(path==='/metal/storage-review-tasks/count')body={code:200,data:{count:1}};
 if(path==='/metal/storage-review-tasks/list')body={code:200,rows:[{id:'61',documentId:'31',documentNumber:'ADJ31'}],total:1};
 if(path==='/metal/storage-orders/options')body={code:200,data:{warehouses:[{id:'1',name:'仓库1',status:'0'}],people:[{id:'9',name:'复验员',canAccept:true,canReceive:true}],stocks:[],customers:[]}};
 await route.fulfill({json:body});});
 await page.goto('/metal/storage-orders');await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'本厂',exact:true}).click();await page.getByRole('tab',{name:'调整 / 归还 / 冲销',exact:true}).click();return doc;
}
for(const piece of [false,true])test(`adjustment columns agree across editor detail and review, mixed=${piece}`,async({page,baseURL})=>{
 const doc=await fixture(page,baseURL!,piece);
 for(const action of ['物料清单','编辑','复验']){
  if(action==='复验'){doc.status='PENDING_REVIEW';doc.creatorId='9';doc.reviewerId='1'}
  await page.getByRole('row').filter({hasText:'ADJ31'}).getByRole('button',{name:action,exact:true}).click();const dialog=page.getByRole('dialog').last();await expect(dialog.getByRole('columnheader',{name:'库存批次',exact:true})).toBeVisible();await expect(dialog.getByText('+100.00',{exact:true})).toBeVisible();await expect(dialog.getByText('工厂自有',{exact:true})).toBeVisible();await expect(dialog.getByText('增加工厂料',{exact:true})).toBeVisible();await expect(dialog.getByRole('columnheader',{name:'件数增减',exact:true})).toHaveCount(piece?1:0);await expect(dialog.getByRole('columnheader',{name:'申报净重（g）',exact:true})).toHaveCount(0);await expect(dialog.getByRole('columnheader',{name:'净重（g）',exact:true})).toHaveCount(0);if(piece)await expect(dialog.getByText('-2.50',{exact:true})).toBeVisible();if(action==='物料清单')await dialog.locator('.el-dialog__headerbtn').click();else await dialog.getByRole('button',{name:'取消',exact:true}).click();
 }
});

