import {test,expect} from '@playwright/test'
import {readFileSync} from 'node:fs'
function setup(){
 const text=readFileSync(new URL('../src/views/metal/warehouse-common/WarehousePage.vue',import.meta.url),'utf8')
 const source=text.split(/\r?\n/).find(l=>l.startsWith('async function load()'))!
 const pending:Array<{resolve:(r:any)=>void,reject:(e:Error)=>void}>=[]
 const warehouseList=()=>new Promise((resolve,reject)=>pending.push({resolve,reject}))
 const tab={value:'RAW'},loading={value:false},failed={value:false},rows={value:[]as any[]},total={value:0},selected={value:[]}
 const load=new Function('warehouseList','kind','query','tabs','tab','loading','failed','rows','total','selected','appliedFilters','let loadEpoch=0;const saving={value:false},adjustVisible={value:false};function invalidateAdjustment(){}'+source+';return load')(warehouseList,'inventory',{pageNum:1},[1],tab,loading,failed,rows,total,selected,{value:{}})
 return{load,pending,tab,loading,failed,rows,total}
}
test('older tab response cannot overwrite newest rows and total',async()=>{
 const s=setup();const raw=s.load();s.tab.value='FINISHED';const finished=s.load()
 s.pending[1].resolve({rows:[{category:'FINISHED'}],total:7});await finished
 s.pending[0].resolve({rows:[{category:'RAW'}],total:99});await raw
 expect(s.rows.value[0].category).toBe('FINISHED');expect(s.total.value).toBe(7);expect(s.loading.value).toBe(false)
})
test('older request failure cannot clear newest loading or mark list failed',async()=>{
 const s=setup();const old=s.load();const latest=s.load();s.pending[0].reject(new Error('fixture'));await old
 expect(s.loading.value).toBe(true);expect(s.failed.value).toBe(false)
 s.pending[1].resolve({rows:[],total:0});await latest;expect(s.loading.value).toBe(false)
})
