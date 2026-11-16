<template>
 <el-dialog v-model="visible" class="acid-wash-dialog" title="历史酸洗记录" width="min(1100px,96vw)" :close-on-click-modal="false" :close-on-press-escape="!busy&&!uncertain" :show-close="!busy&&!uncertain" :before-close="close">
  <div v-loading="loading">
   <p>此入口仅查询、续办及冲销历史独立酸洗批次，保留原批次与仓储单据追溯。新酸洗请在普通生产工单中安排完整工艺路线及酸洗工序，并通过仓储交接完成。</p>
   <el-form label-width="90px" :disabled="busy||loading||uncertain">
    <el-form-item label="工厂"><el-select fit-input-width popper-class="acid-wash-options" v-model="factory" aria-label="酸洗工厂" @change="changeFactory"><el-option v-for="f in factories" :key="f.id" :value="f.id" :label="f.name"/></el-select></el-form-item>
    <template v-if="mode==='list'">

     <el-button :disabled="!factory||loading" @click="loadList">刷新</el-button>
     <el-table :data="batches" border class="acid-table">
      <el-table-column prop="batchNo" label="酸洗批次" min-width="170"/>
      <el-table-column prop="processName" label="工序" min-width="100"/>
      <el-table-column label="状态" min-width="100"><template #default="{row}">{{stateNames[row.status]}}</template></el-table-column>
      <el-table-column label="领料净重（g）" min-width="140"><template #default="{row}">{{decimalText(row.issuedWeight)}}</template></el-table-column>
      <el-table-column label="产出净重（g）" min-width="140"><template #default="{row}">{{decimalText(row.outputWeight)}}</template></el-table-column>
      <el-table-column label="损耗（g）" min-width="110"><template #default="{row}">{{decimalText(row.lossWeight)}}</template></el-table-column>
      <el-table-column label="操作" width="120"><template #default="{row}"><el-button link type="primary" @click="view(row.id)">{{row.status==='OPEN'&&canFinish?'登记产出':'查看详情'}}</el-button></template></el-table-column>
     </el-table>
     <el-pagination v-model:current-page="page" :total="total" :page-size="16" layout="total, prev, pager, next" @current-change="loadList"/>
    </template>
    <template v-else>
     <el-button @click="back">返回批次列表</el-button>
     <template v-if="info">
      <p>{{info.batchNo}} · {{info.process.process_name}} · {{stateNames[info.status]}}</p>
      <p>领料：{{decimalText(info.issuedWeight)}} g；产出：{{decimalText(info.outputWeight)}} g；还料：{{decimalText(info.returnedWeight)}} g；损耗：{{decimalText(info.lossWeight)}} g</p>
      <p v-for="o in info.orders" :key="o.id">{{orderTypeNames[o.type]}}：{{o.orderNo}} / {{o.warehouseName}} / {{stateNames[o.status]}}</p>
      <el-button v-if="canReverse&&info.status!=='VOID'" type="danger" @click="reverse">整体冲销酸洗批次</el-button>
     </template>
     <template v-if="mode==='start'||info?.status==='OPEN'&&canFinish">
      <el-form-item v-if="mode==='start'" label="酸洗工序"><el-select fit-input-width popper-class="acid-wash-options" v-model="process" aria-label="酸洗工序"><el-option v-for="p in options.processes" :key="p.id" :value="p.id" :label="p.name"/></el-select></el-form-item>
      <el-form-item label="日期"><el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" aria-label="酸洗日期"/></el-form-item>
      <el-form-item label="经手人"><el-select fit-input-width popper-class="acid-wash-options" v-model="operators" multiple filterable aria-label="酸洗经手人"><el-option v-for="p in people" :key="p.id" :value="p.id" :label="personLabel(p)"/></el-select></el-form-item>
      <el-form-item label="仓管员"><el-select fit-input-width popper-class="acid-wash-options" v-model="keeper" filterable aria-label="酸洗仓管员"><el-option v-for="p in people" :key="p.id" :value="p.id" :label="personLabel(p)"/></el-select></el-form-item>
      <el-form-item label="说明"><el-input v-model="reason" type="textarea" maxlength="500" show-word-limit aria-label="酸洗说明"/></el-form-item>
      <template v-if="mode==='start'">
       <h3>未处理料领料</h3>
       <div v-for="(r,i) in inputs" :key="i" class="acid-line">
        <el-select fit-input-width popper-class="acid-wash-options" v-model="r.inventoryId" filterable :aria-label="'未处理料来源'+(i+1)"><el-option v-for="s in options.stocks" :key="s.id" :value="s.id" :label="`${s.warehouseName} / ${s.code} / ${s.customerCompanyName} / 账面 ${decimalText(s.netWeight)} g / 占用 ${decimalText(s.reservedWeight)} g / 可领 ${decimalText(s.availableWeight)} g`"/></el-select>
        <BusinessDecimalInput v-model="r.netWeight" :aria-label="'领料净重'+(i+1)" placeholder="领料净重（g）"/>
        <el-button type="danger" link @click="inputs.splice(i,1)">移除</el-button>
       </div>
       <el-button @click="inputs.push({inventoryId:'',netWeight:''})">添加未处理料</el-button>
      </template>
      <template v-else>
       <h3>金属料产出</h3>
       <div v-for="(r,i) in outputs" :key="i" class="acid-line output">
        <el-select fit-input-width popper-class="acid-wash-options" v-model="r.warehouseId" :aria-label="'产出仓库'+(i+1)"><el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.name"/></el-select>
        <el-select fit-input-width popper-class="acid-wash-options" v-model="r.referenceId" filterable :aria-label="'产出物料编码'+(i+1)"><el-option v-for="m in options.materials" :key="m.id" :value="m.id" :label="`${m.code} / ${m.name} / ${m.specification||'—'}`"/></el-select>
        <BusinessDecimalInput v-model="r.netWeight" :aria-label="'产出净重'+(i+1)" placeholder="产出净重（g）"/>
        <el-button type="danger" link @click="outputs.splice(i,1)">移除</el-button>
       </div>
       <el-button @click="outputs.push({warehouseId:'',referenceId:'',netWeight:''})">添加金属料产出</el-button>
       <h3>全部余料还库</h3><p>余料退回原库存；未添加余料表示本批无余料。</p>
       <div v-for="(r,i) in returns" :key="i" class="acid-line">
        <el-select fit-input-width popper-class="acid-wash-options" v-model="r.sourceLineId" :aria-label="'余料来源'+(i+1)"><el-option v-for="s in (info?.sources??[])" :key="s.id" :value="s.id" :label="`${s.warehouseName} / ${s.code} / 领料 ${decimalText(s.netWeight)} g`"/></el-select>
        <BusinessDecimalInput v-model="r.netWeight" :aria-label="'还料净重'+(i+1)" placeholder="还料净重（g）"/>
        <el-button type="danger" link @click="returns.splice(i,1)">移除</el-button>
       </div>
       <el-button @click="returns.push({sourceLineId:'',netWeight:''})">添加余料还库</el-button>
       <p><el-checkbox v-model="checked">已核对全部产出、余料及实际损耗，本次完成整批结算</el-checkbox></p>
      </template>
     </template>
    </template>
   </el-form>
   <p v-if="refreshFailed" role="status">更新检查失败，当前资料可能过期。</p><p v-if="error" role="alert">{{error}}</p><el-button v-if="needsReload&&!uncertain" @click="reloadDetail">重新加载批次</el-button>
  </div>
  <template #footer><el-button v-if="uncertain" type="primary" :loading="busy" @click="retryRequest">重试原请求</el-button><el-button :disabled="busy||uncertain" @click="close()">关闭</el-button><el-button v-if="mode==='start'||info?.status==='OPEN'&&canFinish" type="primary" :loading="busy" :disabled="loading||needsReload||uncertain" @click="submit">{{mode==='start'?'确认酸洗领料':'确认产出及全部还料并结算'}}</el-button></template>
 </el-dialog>
</template>
<script setup lang="ts">
import {ref,computed,onBeforeUnmount,watch} from 'vue'
import {onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import BusinessDecimalInput from '@/components/BusinessDecimalInput.vue'
import {decimalText} from '@/utils/business-decimal'
import {decimalError} from '@/utils/input-validation'
import {personLabel} from '@/utils/person-label'
import {createUUID} from '@/utils/uuid'
import {getToken} from '@/utils/auth'
import useUserStore from '@/store/modules/user'
import {notifyInventoryChanged} from '@/utils/inventory-change'
import {useActionPermission} from '@/composables/useActionPermission'
import {useInventoryRefresh} from '@/composables/useInventoryRefresh'
import {acidOptions,acidList,acidDetail,acidSubmit,warehouseOptions,stateNames,orderTypeNames,type Row} from '@/api/metal/warehouse'
const user=useUserStore()
const emit=defineEmits(['saved','busy-change']),permission=useActionPermission(),canAdd=computed(()=>permission.has('metal:warehouse-order:add')),canFinish=computed(()=>permission.hasAll(['metal:warehouse-order:add','metal:process-batch:settle'])),canReverse=computed(()=>permission.has('metal:warehouse-order:reverse'))
const visible=ref(false),busy=ref(false),loading=ref(false),error=ref(''),needsReload=ref(false),mode=ref('list'),factory=ref(''),factories=ref<Row[]>([]),people=ref<Row[]>([]),warehouses=ref<Row[]>([]),batches=ref<Row[]>([]),total=ref(0),page=ref(1),info=ref<Row>(),options=ref<Row>({stocks:[],processes:[],materials:[]}),process=ref(''),date=ref(''),operators=ref<string[]>([]),keeper=ref(''),reason=ref(''),checked=ref(false),inputs=ref<Row[]>([]),outputs=ref<Row[]>([]),returns=ref<Row[]>([])
const identity=()=>JSON.stringify([user.token,String(user.id),[...user.permissions].sort()]);let openedIdentity=''
const sessionCurrent=()=>user.sessionReady&&!!user.token&&user.token===getToken()&&openedIdentity===identity()
let epoch=0,requestId='',disposed=false,baseline='',optionBaseline=''
const uncertain=ref(false);let pendingRequest:{suffix:string;id:string;payload:Row}|undefined
const stamp=(v:Row)=>JSON.stringify([v.version,v.status,v.sources,v.orders]);
watch(busy,v=>emit('busy-change',v))
const current=(ticket:number)=>()=>!disposed&&visible.value&&epoch===ticket&&sessionCurrent()
function resetForm(){requestId=createUUID();date.value=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date());operators.value=[];keeper.value='';reason.value='';checked.value=false;error.value='';needsReload.value=false;inputs.value=[{inventoryId:'',netWeight:''}];outputs.value=[{warehouseId:'',referenceId:'',netWeight:''}];returns.value=[]}
function close(done?:()=>void){if(busy.value||uncertain.value)return;epoch++;visible.value=false;loading.value=false;info.value=undefined;done?.()}
async function open(factoryId?:string,id?:string){if(busy.value||uncertain.value||!user.sessionReady||!user.token||user.token!==getToken()||!permission.hasAny(['metal:warehouse-order:add','metal:warehouse-order:query']))return;openedIdentity=identity();epoch++;visible.value=true;mode.value='list';info.value=undefined;factory.value='';factories.value=[];batches.value=[];total.value=0;error.value='';loading.value=true;const valid=current(epoch);try{const r=await warehouseOptions('factories',undefined,undefined,valid);if(!valid())return;factories.value=r.data;if(factoryId){if(!r.data.some(f=>String(f.id)===String(factoryId)))throw Error('没有此工厂的酸洗查询权限');factory.value=String(factoryId)}else if(r.data.length===1)factory.value=r.data[0].id;if(id&&factory.value)await view(String(id));else if(factory.value)await changeFactory()}catch(e:any){if(valid())error.value=e.message}finally{if(valid())loading.value=false}}
async function changeFactory(){epoch++;mode.value='list';info.value=undefined;batches.value=[];total.value=0;page.value=1;await loadList()}
async function loadList(){if(!factory.value||busy.value)return;const valid=current(++epoch);loading.value=true;error.value='';try{const r=await acidList(factory.value,page.value,valid);if(valid()){batches.value=r.data.rows;total.value=r.data.total}}catch(e:any){if(valid())error.value=e.message}finally{if(valid())loading.value=false}}
async function loadOptions(valid:()=>boolean){options.value={stocks:[],processes:[],materials:[]};people.value=[];warehouses.value=[];const data=await Promise.all([acidOptions(factory.value,valid),warehouseOptions('people',factory.value,undefined,valid),warehouseOptions('warehouses',factory.value,undefined,valid)]);if(valid()){optionBaseline=JSON.stringify(data.map(r=>r.data));options.value=data[0].data;people.value=data[1].data;warehouses.value=data[2].data}}
async function begin(){if(!canAdd.value||busy.value)return;resetForm();mode.value='start';info.value=undefined;loading.value=true;const valid=current(++epoch);try{await loadOptions(valid);if(valid())process.value=options.value.processes[0]?.id||''}catch(e:any){if(valid())error.value=e.message}finally{if(valid())loading.value=false}}
async function view(id:string){if(busy.value||uncertain.value)return;resetForm();mode.value='detail';info.value=undefined;loading.value=true;const valid=current(++epoch);try{const r=await acidDetail(id,valid);if(!valid())return;info.value=r.data;baseline=stamp(r.data);if(r.data.status==='OPEN'&&canFinish.value)await loadOptions(valid)}catch(e:any){if(valid())error.value=e.message}finally{if(valid())loading.value=false}}
function reloadDetail(){if(info.value)void view(info.value.id)}
function back(){if(busy.value||uncertain.value)return;mode.value='list';info.value=undefined;void loadList()}
function groups(lines:Row[],kind:string){const grouped=new Map<string,Row>();for(const row of lines){const message=decimalError(row.netWeight,'净重');if(message||Number(row.netWeight)<=0)throw Error(message||'净重须大于0');let warehouseId=row.warehouseId;const line:Row={category:'RAW',netWeight:String(row.netWeight),quantity:0};if(kind==='input'){const s=options.value.stocks.find((s:Row)=>s.id===row.inventoryId);if(!s)throw Error('请选择未处理料来源');warehouseId=s.warehouseId;line.inventoryId=s.id}else if(kind==='return'){const s=info.value?.sources.find((s:Row)=>s.id===row.sourceLineId);if(!s)throw Error('请选择本批余料来源');warehouseId=s.warehouseId;line.sourceLineId=s.id}else{if(!options.value.materials.some((m:Row)=>m.id===row.referenceId))throw Error('请选择金属料产出的物料编码');line.referenceId=row.referenceId}if(!warehouseId)throw Error('请选择产出仓库');if(!grouped.has(warehouseId))grouped.set(warehouseId,{warehouseId,lines:[]});grouped.get(warehouseId)!.lines.push(line)}return [...grouped.values()]}
async function submit(){if(!current(epoch)()||busy.value||loading.value||needsReload.value||uncertain.value)return;let payload:Row;const start=mode.value==='start',id=info.value?.id;if(start)return;try{if(!(start?canAdd.value:canFinish.value))return;if(!date.value||!operators.value.length||operators.value.length>20||!keeper.value)throw Error('请填写日期、经手人和仓管员');payload={requestId,orderDate:date.value,operatorIds:[...operators.value],keeperId:keeper.value,reason:reason.value};if(start){const p=options.value.processes.find((p:Row)=>p.id===process.value);if(!p||!inputs.value.length)throw Error('请选择酸洗工序并添加领料明细');Object.assign(payload,{factoryId:factory.value,processId:p.id,processVersion:p.version,groups:groups(inputs.value,'input')})}else{if(!checked.value||!outputs.value.length)throw Error('请登记金属料产出并核对全部实物');Object.assign(payload,{version:info.value!.version,physicalChecked:true,outputs:groups(outputs.value,'output'),returns:groups(returns.value,'return')})}}catch(e:any){error.value=e.message;return}pendingRequest={suffix:`/${id}/finish`,id:String(id),payload:JSON.parse(JSON.stringify(payload))};await retryRequest()}
async function retryRequest(){if(!current(epoch)()||busy.value||!pendingRequest)return;const round=epoch,attempt=pendingRequest,valid=current(round);busy.value=true;error.value='';try{await acidSubmit(attempt.suffix,attempt.payload,valid);if(!valid()||pendingRequest!==attempt)return;uncertain.value=false;pendingRequest=undefined;notifyInventoryChanged();emit('saved');ElMessage.success(attempt.suffix.endsWith('/reverse')?'历史酸洗已整体冲销':'酸洗已结算，金属料可供领用');busy.value=false;await view(attempt.id)}catch(e:any){if(valid()&&pendingRequest===attempt){uncertain.value=typeof e?.code!=='number';if(!uncertain.value)pendingRequest=undefined;error.value=uncertain.value?'提交结果未知，请重试原请求确认结果；当前输入已保留。':e?.message||'提交失败，输入已保留';if(!uncertain.value&&/版本|刷新/.test(error.value))needsReload.value=true}}finally{if(valid())busy.value=false}}
async function reverse(){if(!current(epoch)()||!info.value||!canReverse.value||busy.value||uncertain.value)return;const id=info.value.id,version=info.value.version,ticket=epoch;let reason:string;try{reason=(await ElMessageBox.prompt('填写整体冲销原因；已被后续业务使用的金属料不可冲销。','冲销酸洗批次',{inputValidator:v=>!!v?.trim()&&v.length<=500||'请输入1至500字原因'})).value}catch{return}if(!current(ticket)())return;pendingRequest={suffix:`/${id}/reverse`,id,payload:{version,reason}};await retryRequest()}
const {failed:refreshFailed}=useInventoryRefresh(async ctx=>{if(!factory.value)return;const round=epoch,f=factory.value,p=page.value,valid=()=>ctx.isCurrent()&&current(round)()&&mode.value==='list'&&f===factory.value&&p===page.value;const r=await acidList(f,p,valid,true);if(valid()){batches.value=r.data.rows;total.value=r.data.total}},computed(()=>mode.value!=='list'),{allowed:()=>visible.value&&permission.hasAny(['metal:warehouse-order:add','metal:warehouse-order:query']),reset:()=>{epoch++;visible.value=false;busy.value=false;uncertain.value=false;pendingRequest=undefined;info.value=undefined}},{paused:computed(()=>busy.value||loading.value||uncertain.value),probe:async ctx=>{if(!info.value)return;const round=epoch,id=info.value.id,f=factory.value,valid=()=>ctx.isCurrent()&&current(round)()&&info.value?.id===id&&!busy.value&&!loading.value&&!uncertain.value;const r=await acidDetail(id,valid,true);if(!valid())return;let changed=stamp(r.data)!==baseline;if(info.value?.status==='OPEN'&&canFinish.value){const opts=await Promise.all([acidOptions(f,valid,true),warehouseOptions('people',f,undefined,valid,undefined,true),warehouseOptions('warehouses',f,undefined,valid,undefined,true)]);changed=changed||JSON.stringify(opts.map(o=>o.data))!==optionBaseline}if(valid()&&changed){needsReload.value=true;error.value='业务资料已更新，请重新加载批次并核对；当前输入已保留。'}}})

permission.onChange(()=>{pendingRequest=undefined;uncertain.value=false;busy.value=false;epoch++;visible.value=false;info.value=undefined;factory.value='';batches.value=[]})
watch(visible,v=>{if(!v){epoch++;loading.value=false}})
onBeforeRouteLeave(()=>{if(busy.value||uncertain.value)return false;close()});onBeforeRouteUpdate(()=>{if(busy.value||uncertain.value)return false;close()});onBeforeUnmount(()=>{disposed=true;epoch++});defineExpose({open,close,isOpen:visible})
</script>
<style>
.acid-wash-options{max-width:calc(100vw - 24px)}.acid-wash-options .el-select-dropdown__item{height:auto;min-height:34px;white-space:normal;overflow-wrap:anywhere;line-height:22px;padding:6px 32px 6px 12px}
.acid-wash-dialog .el-table .cell,.acid-wash-dialog .el-table input{text-align:center}
.acid-wash-dialog .acid-line{display:grid;grid-template-columns:minmax(220px,1fr) 180px 52px;gap:10px;margin:10px 0}.acid-wash-dialog .acid-line.output{grid-template-columns:170px minmax(220px,1fr) 180px 52px}.acid-wash-dialog .el-dialog__body{max-height:75vh;overflow:auto}.acid-wash-dialog .acid-table{margin:16px 0}.acid-wash-dialog .el-checkbox{height:auto;white-space:normal}.acid-wash-dialog .el-checkbox__label{white-space:normal}.acid-wash-dialog p{overflow-wrap:anywhere}@media(max-width:768px){.acid-wash-dialog .acid-line,.acid-wash-dialog .acid-line.output{grid-template-columns:1fr}.acid-wash-dialog .el-select,.acid-wash-dialog .el-date-editor{width:100%;max-width:100%}.acid-wash-dialog .el-dialog__footer{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px}.acid-wash-dialog .el-form-item__label{width:82px!important;white-space:nowrap}}
</style>
