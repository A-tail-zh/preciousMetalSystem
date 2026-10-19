import {test,expect} from '@playwright/test'
import {readFileSync} from 'node:fs'
function reportFixture(){
 const file=readFileSync(new URL('../src/views/metal/warehouse-common/WorkReportDialog.vue',import.meta.url),'utf8')
 const source=file.split(/\r?\n/).find(s=>s.startsWith('async function loadDetail()'))!
 const reportId={value:'A'},report={value:undefined as any},loading={value:false},pending:Array<(v:any)=>void>=[]
 const warehouseDetail=()=>new Promise(resolve=>pending.push(resolve))
 const load=new Function('warehouseDetail','reportId','report','loading',"let detailEpoch=0,openEpoch=0,disposed=false;const props={workOrderId:'test'},reversing={value:false};const isViewCurrent=()=>true;function invalidateCorrection(){}"+source+';return loadDetail')(warehouseDetail,reportId,report,loading)
 return{load,reportId,report,loading,pending}
}
test('report selection retains the newest batch when replies arrive out of order',async()=>{
 const s=reportFixture();const first=s.load();s.reportId.value='B';const second=s.load();s.pending[1]({data:{id:'B',batchId:'batch-B'}});await second;s.pending[0]({data:{id:'A',batchId:'batch-A'}});await first
 expect(s.report.value.id).toBe('B');expect(s.report.value.batchId).toBe('batch-B')
})
test('changing report clears previous evidence until newest response arrives',async()=>{
 const s=reportFixture();s.report.value={id:'A'};s.reportId.value='B';const pending=s.load();expect(s.report.value).toBeUndefined();expect(s.loading.value).toBe(true);s.pending[0]({data:{id:'B'}});await pending;expect(s.loading.value).toBe(false)
})
test('warehouse details retain the newest document during out of order replies',async()=>{
 const text=readFileSync(new URL('../src/views/metal/warehouse-common/WarehousePage.vue',import.meta.url),'utf8');const source=text.split(/\r?\n/).find(s=>s.startsWith('async function view('))!.replace(':Row','')
 const pending:Array<(v:any)=>void>=[],warehouseDetail=()=>new Promise(resolve=>pending.push(resolve)),detail={value:undefined as any},reason={value:''},completedQuantity={value:1},detailVisible={value:false},saving={value:false}
 const view=new Function('warehouseDetail','kind','detail','reason','completedQuantity','detailVisible','saving',"let detailEpoch=0,openEpoch=0,disposed=false;const props={workOrderId:'test'},reversing={value:false};const isViewCurrent=()=>true;function invalidateCorrection(){}"+source+';return view')(warehouseDetail,'warehouse-order',detail,reason,completedQuantity,detailVisible,saving)
 const first=view({id:'A'}),second=view({id:'B'});pending[1]({data:{id:'B'}});await second;pending[0]({data:{id:'A'}});await first;expect(detail.value.id).toBe('B')
})

// Correction target/duplicate checks now run through the controlled dialog in report-correction-lifecycle.spec.ts.
