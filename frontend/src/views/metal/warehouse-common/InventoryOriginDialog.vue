<template>
 <el-dialog class="origin-verification-dialog" v-model="visible" title="来源资格核对" width="min(700px,94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy&&!uncertain" :show-close="!busy&&!uncertain" @closed="!visible&&clear()">
  <p>{{stock?.code}} / {{stock?.name}}。核对须有实际凭据，不生成加工报工或产出。</p>
  <el-form label-position="top" :disabled="busy||loading||uncertain">
   <el-form-item label="粉料来源"><el-select v-model="form.powderOrigin" aria-label="核对粉料来源"><el-option v-for="(label,value) in powderOriginNames" :key="value" :label="label" :value="value"/></el-select></el-form-item>
   <el-form-item label="酸洗资格"><el-select v-model="form.washingStatus" aria-label="核对酸洗资格"><el-option v-for="(label,value) in washingStatusNames" :key="value" :label="label" :value="value"/></el-select></el-form-item>
   <el-form-item v-if="stock?.category==='SEMI'" label="融定资格"><el-select v-model="form.smeltingStatus" aria-label="核对融定资格"><el-option label="待核对" value="UNVERIFIED"/><el-option label="融定合格" value="PASSED"/></el-select></el-form-item>
   <el-form-item label="核对凭据与说明"><el-input v-model="form.evidence" type="textarea" maxlength="500" show-word-limit aria-label="核对凭据与说明"/></el-form-item>
  </el-form>
  <p v-if="refreshFailed" role="status">更新检查失败，当前资料可能过期。</p><el-button v-if="needsReload&&!uncertain" @click="recheck">重新核对当前资料</el-button><el-alert v-if="error" :title="error" type="error" :closable="false"/>
  <h4>资格核对历史</h4><el-button :disabled="busy||loading||uncertain" link type="primary" @click="loadHistory">刷新历史</el-button>
  <el-table v-loading="loading" :data="history" border><el-table-column label="时间" prop="createTime" min-width="165" :formatter="dateTimeCell"/><el-table-column label="粉料来源" min-width="115"><template #default="{row}">{{powderOriginNames[row.newPowderOrigin]||row.newPowderOrigin}}</template></el-table-column><el-table-column label="酸洗资格" min-width="115"><template #default="{row}">{{washingStatusNames[row.newWashingStatus]||row.newWashingStatus}}</template></el-table-column><el-table-column label="融定资格" min-width="115"><template #default="{row}">{{row.newSmeltingStatus==='PASSED'?'融定合格':'待核对'}}</template></el-table-column><el-table-column label="核对人" prop="actorName" min-width="110"/><el-table-column label="凭据" prop="evidence" min-width="180"/></el-table>
  <template #footer><el-button v-if="uncertain" type="primary" :loading="busy" @click="retryRequest">重试原请求</el-button><el-button :disabled="busy||uncertain" @click="visible=false">关闭</el-button><el-button v-if="permission.has('metal:inventory:verify-origin')" type="primary" :disabled="loading||needsReload||uncertain" :loading="busy" @click="save">保存来源核对</el-button></template>
 </el-dialog>
</template>
<script setup lang="ts">
import {ref,reactive,computed,onBeforeUnmount} from 'vue'
import {onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
import {ElMessage} from 'element-plus'
import {useActionPermission} from '@/composables/useActionPermission'
import useUserStore from '@/store/modules/user'
import {createUUID} from '@/utils/uuid'
import {getToken} from '@/utils/auth'
import {useInventoryRefresh} from '@/composables/useInventoryRefresh'
import {dateTimeCell} from '@/utils/display-date-time'
import {notifyInventoryChanged} from '@/utils/inventory-change'
import {inventoryOriginHistory,verifyInventoryOrigin,powderOriginNames,washingStatusNames,type Row} from '@/api/metal/warehouse'
const emit=defineEmits<{saved:[]}>(),permission=useActionPermission(),user=useUserStore()
const visible=ref(false),busy=ref(false),loading=ref(false),needsReload=ref(false),error=ref(''),stock=ref<Row>(),history=ref<Row[]>([]),form=reactive({powderOrigin:'UNKNOWN',washingStatus:'UNVERIFIED',smeltingStatus:'UNVERIFIED',evidence:''})
let epoch=0,disposed=false,attemptBody='',attemptId='',historyBaseline=''
const uncertain=ref(false);let pendingRequest:{id:string;payload:Row}|undefined
const identity=()=>JSON.stringify([user.token,String(user.id),[...user.permissions].sort()]);let openedIdentity=''
const sessionCurrent=()=>user.sessionReady&&!!user.token&&user.token===getToken()&&openedIdentity===identity()
const current=(round:number)=>()=>!disposed&&visible.value&&round===epoch&&sessionCurrent()&&permission.has('metal:inventory:verify-origin')
function clear(){epoch++;busy.value=false;loading.value=false;uncertain.value=false;pendingRequest=undefined;stock.value=undefined;history.value=[];error.value='';attemptBody='';attemptId=''}
async function open(row:Row){if(busy.value||uncertain.value||!user.sessionReady||!user.token||user.token!==getToken())return;if(!permission.has('metal:inventory:verify-origin'))return;clear();openedIdentity=identity();stock.value={...row};Object.assign(form,{powderOrigin:row.powderOrigin??'UNKNOWN',washingStatus:row.washingStatus??'UNVERIFIED',smeltingStatus:row.smeltingStatus??'UNVERIFIED',evidence:''});needsReload.value=false;visible.value=true;await loadHistory()}
async function loadHistory(){if(!stock.value||busy.value||uncertain.value)return;const round=epoch,id=stock.value.id;loading.value=true;error.value='';try{const r=await inventoryOriginHistory(id,current(round));if(current(round)()){history.value=r.data;historyBaseline=JSON.stringify(r.data)}}catch(e:any){if(current(round)())error.value=e.message||'历史加载失败'}finally{if(current(round)())loading.value=false}}
async function save(){if(!current(epoch)()||!stock.value||busy.value||loading.value||needsReload.value||uncertain.value||!permission.has('metal:inventory:verify-origin'))return;if(!form.evidence.trim()){error.value='请填写核对凭据与说明';return}const payload={version:stock.value.version,...form,evidence:form.evidence.trim()},body=JSON.stringify(payload);if(body!==attemptBody){attemptBody=body;attemptId=createUUID()}pendingRequest={id:String(stock.value.id),payload:JSON.parse(JSON.stringify({requestId:attemptId,...payload}))};await retryRequest()}
async function retryRequest(){if(!current(epoch)()||busy.value||!pendingRequest)return;const round=epoch,attempt=pendingRequest;busy.value=true;try{await verifyInventoryOrigin(attempt.id,attempt.payload,current(round));if(!current(round)()||pendingRequest!==attempt)return;uncertain.value=false;pendingRequest=undefined;notifyInventoryChanged();ElMessage.success('来源资格核对已登记');emit('saved');visible.value=false}catch(e:any){if(current(round)()&&pendingRequest===attempt){uncertain.value=typeof e?.code!=='number';if(!uncertain.value)pendingRequest=undefined;error.value=uncertain.value?'提交结果未知，请重试原请求确认结果；核对凭据已保留。':e?.message||'核对失败，输入已保留';if(!uncertain.value&&/版本|刷新|重新加载/.test(error.value))needsReload.value=true}}finally{if(current(round)())busy.value=false}}
async function recheck(){if(!stock.value||busy.value||uncertain.value)return;const round=epoch,id=stock.value.id;loading.value=true;try{const r=await inventoryOriginHistory(id,current(round));if(!current(round)())return;history.value=r.data;historyBaseline=JSON.stringify(r.data);if(r.version!=null)stock.value.version=r.version;needsReload.value=false;error.value='';attemptBody='';attemptId='';ElMessage.info('已重新读取当前库存版本，请核对凭据与资格后再保存')}catch(e:any){if(current(round)())error.value=e.message}finally{if(current(round)())loading.value=false}}
const {failed:refreshFailed}=useInventoryRefresh(async()=>{},computed(()=>true),{allowed:()=>visible.value&&permission.has('metal:inventory:verify-origin'),reset:()=>{visible.value=false;clear()}},{paused:computed(()=>busy.value||loading.value||uncertain.value),probe:async ctx=>{if(!stock.value)return;const round=epoch,id=stock.value.id,valid=()=>ctx.isCurrent()&&current(round)()&&stock.value?.id===id&&!busy.value&&!loading.value&&!uncertain.value;const r=await inventoryOriginHistory(id,valid,true);if(valid()&&(r.version!=null&&String(r.version)!==String(stock.value?.version)||JSON.stringify(r.data)!==historyBaseline)){needsReload.value=true;error.value='业务资料已更新，请重新核对当前资料；核对凭据已保留。'}}})
permission.onChange(()=>{visible.value=false;clear()});onBeforeRouteLeave(()=>{if(busy.value||uncertain.value)return false;visible.value=false;clear()});onBeforeRouteUpdate(()=>{if(busy.value||uncertain.value)return false;visible.value=false;clear()});onBeforeUnmount(()=>{disposed=true;epoch++});defineExpose({open,isOpen:visible})
</script>

<style scoped>.origin-verification-dialog :deep(.el-table .cell),.origin-verification-dialog :deep(.el-table input){text-align:center}</style>
