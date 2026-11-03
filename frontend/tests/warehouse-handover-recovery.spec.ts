import {test,expect} from '@playwright/test'
import {readFileSync} from 'node:fs'
import ts from 'typescript'
const source=readFileSync(new URL('../src/views/metal/warehouse-common/WarehouseHandoverDialog.vue',import.meta.url),'utf8')
const js=(s:string)=>ts.transpileModule(s,{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText
const load=js(source.slice(source.indexOf('async function loadBatch()'),source.indexOf('\nfunction addOutput()')))
const save=js(source.slice(source.indexOf('async function save()'),source.indexOf('\nconst {failed:refreshFailed}')))
function fixture(){
 const pending:any[]=[],sent:any[]=[]
 const setup="let epoch=0,disposed=false,attemptBody='',attemptId='',baseline='',pendingRequest;const stamp=JSON.stringify;const r=value=>({value}),visible=r(true),user={id:'ACTOR',sessionReady:true};const current=round=>()=>visible.value&&round===epoch;const batchId=r('B'),loading=r(false),error=r(''),context=r({batchId:'A',productId:'P',version:0,measurementMode:'PIECE',outputType:'FINISHED_IN'}),outputs=r([{warehouseId:'W',keeperId:'K',netWeight:'48',quantity:10}]),returns=r([]),checked=r(true),needsReload=r(false),residualMode=r('NONE'),uncertain=r(false),rawOutput=r(false),outputMaterials=r([]),busy=r(false),date=r('2026-11-03'),reason=r(''),weightMode=r(false);const createUUID=()=> 'mock-uuid',ElMessage={success(){}},emit=()=>{},addOutput=()=>{};"
 const factory=new Function('warehouseHandoverContext','warehouseFlowAction',setup+load+';'+save+';return{loadBatch,save,context,outputs,returns,batchId,error,checked,loading};')
 return {...factory(()=>new Promise((resolve,reject)=>pending.push({resolve,reject})),async(suffix:string,payload:any)=>{sent.push({suffix,payload});return{data:{id:'FLOW'}}}),pending,sent}
}
test('failed batch switch clears prior weights and blocks submission',async()=>{const f=fixture(),loading=f.loadBatch();expect(f.context.value).toBeUndefined();expect(f.outputs.value).toEqual([]);expect(f.checked.value).toBe(false);f.pending[0].reject(Error('加载失败'));await loading;await f.save();expect(f.sent).toEqual([])})
test('late success cannot restore a batch after the newer selection failed',async()=>{const f=fixture(),a=f.loadBatch();f.batchId.value='C';const b=f.loadBatch();f.pending[1].reject(Error('新批次失败'));await b;f.pending[0].resolve({data:{batchId:'B',productId:'P'}});await a;expect(f.context.value).toBeUndefined();expect(f.error.value).toBe('新批次失败')})
test('clearing batch selection invalidates a pending context request',async()=>{const f=fixture(),a=f.loadBatch();f.batchId.value='';await f.loadBatch();f.pending[0].resolve({data:{batchId:'B',productId:'P'}});await a;expect(f.context.value).toBeUndefined();expect(f.loading.value).toBe(false)})
for(const mode of ['WEIGHT','PIECE'])test('reopened '+mode+' semi return retains its own count semantics',async()=>{
 const f=fixture(),loading=f.loadBatch(),weight=mode==='WEIGHT';
 f.pending[0].resolve({data:{batchId:'B',productId:'P',version:2,measurementMode:'PIECE',outputType:'FINISHED_IN',sources:[{lineId:'SOURCE',measurementMode:mode}],flow:{groups:[{processorId:'ACTOR',type:'FINISHED_IN',warehouseId:'W',keeperId:'K',lines:[{processorWeight:'48',line:{category:'FINISHED',quantity:10}}]},{processorId:'ACTOR',type:'SEMI_RETURN',warehouseId:'W',keeperId:'K',lines:[{processorWeight:'3',line:{category:'SEMI',sourceLineId:'SOURCE',netWeight:'3',quantity:weight?0:2,referenceQuantity:weight?5:null}}]}]}}});
 await loading;expect(f.returns.value[0].measurementMode).toBe(mode);expect(f.returns.value[0].quantity).toBe(weight?5:2);f.checked.value=true;await f.save();expect(f.sent).toHaveLength(1);const line=f.sent[0].payload.documents.find((d:any)=>d.type==='SEMI_RETURN').lines[0];expect(line.quantity).toBe(weight?0:2);expect(line.referenceQuantity).toBe(weight?5:null)
})
