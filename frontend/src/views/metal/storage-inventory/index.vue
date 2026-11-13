<template>
<section class="storage-page storage-inventory-page">
 <FactoryToolbar :before-change="beforeChange"><el-select v-model="filters.warehouseId" :disabled="!state.factoryId" placeholder="仓库" clearable><el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id"/></el-select><el-input v-model="filters.name" :disabled="!state.factoryId" :placeholder="tab==='RAW'?'物料名称':'产品名称'" @keyup.enter="search"/><el-input v-model="filters.specification" :disabled="!state.factoryId" placeholder="规格型号"/><el-select v-model="filters.customerId" :disabled="!state.factoryId" placeholder="客户料权" clearable><el-option label="工厂自有" value="0"/><el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id"/></el-select><el-button class="list-action" :disabled="!state.factoryId" @click="search">查询</el-button><el-button class="list-action list-action--neutral" @click="reset">重置</el-button><el-button v-if="can('metal:inventory:export')" class="list-action" :disabled="!state.factoryId||loading" @click="exportRows">导出</el-button><el-button v-if="can('metal:material-debt:list')" @click="debtsOpen=true">借还料账</el-button><el-button v-if="can('metal:inventory:adjust')" :disabled="!state.factoryId" @click="openAdjustment()">库存调整</el-button></FactoryToolbar>
 <el-alert v-if="error" :title="error" type="error" :closable="false"/><el-button v-if="error" @click="load">重试</el-button>
 <el-tabs v-model="tab" @tab-change="reset"><el-tab-pane label="原材料" name="RAW"/><el-tab-pane label="成品" name="PRODUCT"/><el-tab-pane label="半成品" name="SEMI"/></el-tabs>
 <el-table :data="rows" border v-loading="loading"><el-table-column v-for="c in columns" :key="c.key" :label="c.label" :min-width="c.width||130"><template #default="{row}">{{cell(row,c.key)}}</template></el-table-column><el-table-column label="操作" width="190" fixed="right"><template #default="{row}"><el-button v-if="can('metal:inventory:query')" class="list-action list-action--primary list-action--row" @click="showDetail(row)">查看详情</el-button><el-button v-if="can('metal:inventory:adjust')" class="list-action list-action--edit list-action--row" @click="openAdjustment(row)">库存调整</el-button></template></el-table-column><template #empty>{{state.factoryId?'暂无库存':'请选择所属工厂'}}</template></el-table>
 <div class="storage-pagination"><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[16,32,64]" :total="total" layout="total,sizes,prev,pager,next,jumper" @change="changePage"/></div>
 <el-dialog v-model="groupOpen" title="原材料汇总详情" width="1050px" class="storage-dialog">
  <template v-if="groupDetail"><el-descriptions :column="2" border><el-descriptions-item v-for="c in columns" :key="c.key" :label="c.label">{{cell(groupDetail,c.key)}}</el-descriptions-item></el-descriptions>
   <h3>各公司剩余料权</h3><el-table :data="groupDetail.ownerships" border class="group-owners"><el-table-column prop="companyName" label="公司"/><el-table-column prop="ownerName" label="料权方"/><el-table-column label="剩余净重（g）"><template #default="{row}">{{decimalText(row.weight)}}</template></el-table-column><el-table-column prop="quantity" label="剩余件数"/></el-table>
   <h3>来源库存明细</h3><el-table :data="groupDetail.stocks" border class="group-sources"><el-table-column prop="batchNo" label="实物库存号" min-width="200"/><el-table-column label="来源工单"><template #default="{row}">{{row.originOrderId?`工单 #${row.originOrderId}`:'—'}}</template></el-table-column><el-table-column label="剩余净重（g）"><template #default="{row}">{{decimalText(row.netWeight)}}</template></el-table-column><el-table-column prop="quantity" label="剩余件数"/><el-table-column label="操作" width="200"><template #default="{row}"><el-button v-if="can('metal:inventory:query')" link @click="showDetail(row)">查看来源流水</el-button><el-button v-if="can('metal:inventory:adjust')" link @click="openAdjustment(row)">库存调整</el-button></template></el-table-column></el-table>
  </template>
 </el-dialog>
 <el-dialog v-model="detailOpen" title="库存详情" width="1050px" class="storage-dialog"><template v-if="detail"><el-descriptions :column="2" border><el-descriptions-item v-for="c in columns.filter(c=>c.key!=='sourceCount')" :key="c.key" :label="c.label">{{cell(detail,c.key)}}</el-descriptions-item></el-descriptions><el-descriptions v-if="tab==='RAW'" :column="2" border><el-descriptions-item label="实物库存号">{{detail.batchNo}}</el-descriptions-item></el-descriptions><el-table :data="detail.ownerships||[]" border><el-table-column prop="companyName" label="公司"/><el-table-column prop="ownerName" label="料权方"/><el-table-column label="净重（g）"><template #default="{row}">{{decimalText(row.weight)}}</template></el-table-column><el-table-column prop="quantity" label="件数"/></el-table><h3>生产批次库存份额</h3><el-table :data="detail.stockSlices||[]" border class="stock-slices"><el-table-column prop="stockSliceId" label="库存份额ID"/><el-table-column label="生产批次"><template #default="{row}">{{row.sourceBatchId?`PB身份 #${row.sourceBatchId}`:'未绑定生产批次'}}</template></el-table-column><el-table-column label="份额净重（g）"><template #default="{row}">{{decimalText(row.balanceWeight??row.weight)}}</template></el-table-column><el-table-column prop="balanceQuantity" label="份额件数"/><el-table-column label="操作"><template #default="{row}"><el-button v-if="row.sourceBatchId&&can('metal:work-order:query')" link @click="traceDialog?.open({id:row.sourceBatchId} as Row)">追溯生产批次</el-button></template></el-table-column></el-table><h3>实物流水</h3><el-table :data="ledger.filter(row=>row.ledger==='PHYSICAL')" border class="physical-ledger"><el-table-column prop="documentNumber" label="关联单据"/><el-table-column label="类型"><template #default="{row}">{{ledgerActions[row.action]||row.action}}</template></el-table-column><el-table-column label="净重变动（g）"><template #default="{row}">{{decimalText(row.weightDelta)}}</template></el-table-column><el-table-column prop="quantityDelta" label="件数变动"/><el-table-column label="时间" min-width="170"><template #default="{row}">{{displayDateTime(row.createTime)}}</template></el-table-column></el-table></template></el-dialog>
 <BatchTraceDialog ref="traceDialog" :factory-id="state.factoryId" :identity="context()" @changed="refreshDetail"/>
 <OrderEditor ref="editor" @saved="refreshDetail"/>
 <DebtPanel v-model="debtsOpen" @repay="repay"/>
</section>
</template>
<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue';import {onBeforeRouteLeave} from 'vue-router';import {ElMessage} from 'element-plus';import request from '@/utils/request'
import {rawInventoryGroups,inventoryOwners} from './groups'
import {displayDateTime} from '@/utils/display-date-time'
const ledgerActions:Record<string,string>={INBOUND:'入库',ISSUE:'生产领料',DISPATCH:'提料出库',RETURN:'原料退库',REPAYMENT:'料权归还',ADJUSTMENT:'库存调整',REVERSAL:'冲销'}
import BatchTraceDialog from '../production-order/BatchTraceDialog.vue';import FactoryToolbar from '../storage-live/FactoryToolbar.vue';import {useStorage,message,type Row} from '../storage-live/client';import {decimalText} from '@/utils/business-decimal';import {downloadCsv} from '../storage-preview/csv';import OrderEditor from '../storage-orders/OrderEditor.vue';import DebtPanel from '../storage-live/DebtPanel.vue'
const {state,can,context}=useStorage(),tab=ref('RAW'),page=ref(1),pageSize=ref(16),rows=ref<Row[]>([]),total=ref(0),loading=ref(false),error=ref(''),warehouses=ref<Row[]>([]),customers=ref<Row[]>([]),detailOpen=ref(false),detail=ref<Row>(),ledger=ref<Row[]>([]),debtsOpen=ref(false),editor=ref<InstanceType<typeof OrderEditor>>()
const rawGroups=ref<Row[]>([]),groupOpen=ref(false),groupDetail=ref<Row>();let detailEpoch=0
const traceDialog=ref<InstanceType<typeof BatchTraceDialog>>()
const blank=()=>({warehouseId:'',name:'',specification:'',customerId:''}),filters=ref(blank());let epoch=0,alive=true
const columns=computed(()=>[{key:'code',label:tab.value==='RAW'?'物料编码':'产品编码'},{key:'name',label:tab.value==='RAW'?'物料名称':'产品名称'},...(tab.value==='RAW'?[{key:'form',label:'材料形态'}]:[{key:'productType',label:'产品类型'}]),{key:'netWeight',label:'实物净重（g）'},{key:'quantity',label:'数量（件）'},{key:'specification',label:'规格型号'},{key:'metalType',label:'金属类型'},...(tab.value==='SEMI'?[{key:'processName',label:'加工阶段'}]:[]),...(tab.value==='RAW'?[{key:'purity',label:'纯度（%）'},{key:'measurementMode',label:'计量口径'},{key:'unit',label:'单位'},{key:'sourceCount',label:'来源库存数'}]:[{key:'batchNo',label:'实物库存号',width:200}]),{key:'warehouseName',label:'仓库'}])
function cell(row:Row,key:string){const value=row[key]??row.identity?.[key];if(key==='measurementMode')return value==='PIECE'?'按件':'按重量';if(key==='netWeight')return decimalText(value);if(key==='warehouseName')return warehouses.value.find(w=>w.id===row.warehouseId)?.name||value||'—';if(key==='form')return ({POWDER:'粉状',UNTREATED:'未处理料',METAL:'金属料'} as any)[value]||value||'—';return value??'—'}
function changePage(){if(tab.value==='RAW')rows.value=rawGroups.value.slice((page.value-1)*pageSize.value,page.value*pageSize.value);else void load()}
async function load(){
 const id=++epoch,current=context(),params={factoryId:state.factoryId,category:tab.value,...filters.value},active=()=>current===context()&&id===epoch&&alive
 rows.value=[];rawGroups.value=[];total.value=0
 if(!state.factoryId)return
 loading.value=true;error.value=''
 try{
  if(params.category==='RAW'){
   const all:Row[]=[];let count=0
   for(let p=1;p===1||all.length<count;p++){
    const r:any=await request({url:'/metal/storage-inventory/list',params:{...params,pageNum:p,pageSize:500},quiet:true})
    if(!active())return
    const nextCount=Number(r.total||0);if(p>1&&nextCount!==count)throw Error('库存发生变化，请重新查询');count=nextCount;if(!r.rows?.length&&all.length<count)throw Error('库存发生变化，请重新查询')
    all.push(...(r.rows||[]))
   }
   if(all.length!==count||new Set(all.map(row=>row.id)).size!==all.length)throw Error('库存发生变化，请重新查询')
   rawGroups.value=rawInventoryGroups(all) as Row[];total.value=rawGroups.value.length;changePage()
  }else{
   const r:any=await request({url:'/metal/storage-inventory/list',params:{...params,pageNum:page.value,pageSize:pageSize.value},quiet:true})
   if(active()){rows.value=r.rows||[];total.value=r.total||0}
  }
 }catch(e){if(active()){rows.value=[];rawGroups.value=[];total.value=0;error.value=message(e)}}finally{if(id===epoch)loading.value=false}
}
async function loadOptions(){const current=context();if(!state.factoryId)return;try{const [w,c]:any[]=await Promise.all([request({url:'/metal/storage-warehouses/options',params:{factoryId:state.factoryId},quiet:true}),request({url:'/metal/options/customers',params:{factoryId:state.factoryId},quiet:true})]);if(current===context()){warehouses.value=w.data||[];customers.value=c.data||[]}}catch(e){if(current===context())error.value=message(e)}}
function search(){page.value=1;detailEpoch++;groupOpen.value=false;detailOpen.value=false;void load()}function reset(){filters.value=blank();page.value=1;detailEpoch++;detailOpen.value=false;groupOpen.value=false;void load()}
async function showDetail(row:Row){
 if(row.groupKey){await showGroup(row);return}
 const current=context(),ticket=++detailEpoch;try{const [d,l]:any[]=await Promise.all([request({url:`/metal/storage-inventory/${row.id}`,params:{factoryId:state.factoryId}}),request({url:`/metal/storage-inventory/${row.id}/ledger`,params:{factoryId:state.factoryId}})]);if(current===context()&&ticket===detailEpoch&&alive){detail.value=d.data;ledger.value=l.data||l.rows||[];detailOpen.value=true}}catch(e){error.value=message(e)}}
async function showGroup(row:Row){
 const current=context(),ticket=++detailEpoch,stocks:Row[]=[]
 try{
  for(let i=0;i<row.stocks.length;i+=20){
   const results:any[]=await Promise.all(row.stocks.slice(i,i+20).map((stock:Row)=>request({url:`/metal/storage-inventory/${stock.id}`,params:{factoryId:state.factoryId}})))
   if(current!==context()||ticket!==detailEpoch||!alive)return
   stocks.push(...results.map(r=>r.data))
  }
  const summaries=rawInventoryGroups(stocks);if(summaries.length!==1||summaries[0].groupKey!==row.groupKey)throw Error('库存身份发生变化，请重新查询');const summary=summaries[0]
  groupDetail.value={...row,...summary,stocks,ownerships:inventoryOwners(stocks)};groupOpen.value=true
 }catch(e){if(current===context()&&ticket===detailEpoch)error.value=message(e)}
}
function openAdjustment(row?:Row){const warehouseIds=row?.groupKey?[String(row.warehouseId)]:[]
 if(row?.groupKey)row=row.stocks.length===1?row.stocks[0]:undefined
 editor.value?.open('ADJUST',undefined,{type:'ADJUSTMENT',warehouseIds,lines:row?[{stockId:row.id,warehouseId:row.warehouseId,category:row.category,measurementMode:row.measurementMode,batchNo:row.batchNo,code:row.code||row.identity?.code,name:row.name||row.identity?.name,ownerId:'0',deltaWeight:'',deltaQuantity:'',reason:''}]:[]})}
function repay(row:Row){debtsOpen.value=false;editor.value?.open('RETURN_RIGHTS',undefined,{type:'OWNERSHIP_RETURN',debt:row})}
async function exportRows(){const current=context(),category=tab.value,ticket=epoch,headers=columns.value.map(c=>({...c})),all:Row[]=[];try{const r:any=await request({url:'/metal/storage-inventory/export',params:{factoryId:state.factoryId,category:tab.value,...filters.value}});if(current!==context()||ticket!==epoch||category!==tab.value||!alive)return;all.push(...(category==='RAW'?rawInventoryGroups(r.data):r.data));downloadCsv('库存',headers.map(c=>c.label),all.map(r=>headers.map(c=>String(cell(r,c.key)))))}catch(e){ElMessage.error(message(e))}}
async function refreshDetail(){await load();if(groupDetail.value&&groupOpen.value){const latest=rawGroups.value.find(row=>row.groupKey===groupDetail.value?.groupKey);if(latest)await showGroup(latest);else groupOpen.value=false}if(detail.value&&detailOpen.value)await showDetail(detail.value)}
async function beforeChange(){if(traceDialog.value&&!traceDialog.value.requestClose())return false;return await editor.value?.requestClose()??true}
watch(()=>state.factoryId,()=>{epoch++;detailEpoch++;rawGroups.value=[];groupOpen.value=false;rows.value=[];warehouses.value=[];customers.value=[];detailOpen.value=false;debtsOpen.value=false;reset();void loadOptions()},{immediate:true});onBeforeRouteLeave(beforeChange);onBeforeUnmount(()=>{alive=false;epoch++;detailEpoch++})
</script>
<style scoped>.storage-inventory-page :deep(.storage-filter){flex-wrap:wrap}.storage-inventory-page :deep(.storage-filter .el-input),.storage-inventory-page :deep(.storage-filter .el-select){width:150px;flex:1 1 130px;min-width:100px}@media(min-width:1100px){.storage-inventory-page :deep(.storage-filter){flex-wrap:nowrap}}</style>
