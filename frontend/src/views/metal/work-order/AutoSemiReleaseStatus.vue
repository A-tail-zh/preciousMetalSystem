<template><section v-if="failed||hasActivity" class="auto-status" v-loading="loading"><div class="heading"><strong>正常完成后自动释放</strong><el-button :disabled="loading" @click="load">刷新状态</el-button></div><p v-if="failed" role="alert">自动释放状态更新失败，当前显示可能过期，请重试。</p><template v-if="data"><p>{{data.autoSemiReleaseEligible?'本工单适用正常完成自动释放；部分关闭仍需单独确认。':'历史或未启用自动释放的工单，余量须单独确认释放。'}}</p><p v-if="data.event">处理状态：{{data.event?names[data.event.status]??data.event.status:'尚未触发'}} {{data.event?.message}}</p><div class="scroll"><el-table v-if="data.results?.length" :data="data.results" border><el-table-column prop="inventoryId" label="来源库存" min-width="120"/><el-table-column prop="quantity" label="释放件数" min-width="100"/><el-table-column prop="netWeight" label="释放净重(g)" min-width="150" :formatter="decimalColumn"/><el-table-column label="结果" min-width="140"><template #default="{row}">{{names[row.status]??row.status}}</template></el-table-column><el-table-column prop="message" label="原因" min-width="280"/></el-table></div></template></section></template>
<script setup lang="ts">
import {decimalText,decimalColumn,businessValue} from '@/utils/business-decimal'

import {computed,ref,watch,onBeforeUnmount} from 'vue'
import {useInventoryRefresh,type InventoryRefreshContext} from '@/composables/useInventoryRefresh'
import {useActionPermission} from '@/composables/useActionPermission'
import {getToken} from '@/utils/auth'
import useUserStore from '@/store/modules/user'
import request from '@/utils/request'
const props=defineProps<{workOrderId:string}>(),data=ref<any>(),loading=ref(false),failed=ref(false),user=useUserStore(),permission=useActionPermission()
let epoch=0,disposed=false
const names:Record<string,string>={PENDING:'待处理',RETRY:'重试中',FAILED:'技术失败',RELEASED:'已自动释放',PARTIAL:'部分完成',REVIEW:'待核对',NO_BALANCE:'无需释放',ALREADY_HANDLED:'已处理，不重复释放',CANCELLED:'已取消'}
const canRead=()=>permission.has('metal:work-order:query')&&!!user.token&&user.token===getToken()
function resetAccess(){epoch++;data.value=undefined;loading.value=false;failed.value=false}
async function load(){return readStatus(false)}
async function readStatus(background:boolean,refresh?:InventoryRefreshContext){
 if(!canRead()){resetAccess();return false}if(loading.value)return false
 const ticket=++epoch,work=props.workOrderId,session=user.token,account=String(user.id),valid=()=>!disposed&&ticket===epoch&&work===props.workOrderId&&session===user.token&&account===String(user.id)&&canRead(),current=()=>valid()&&(!refresh||refresh.isCurrent())
 loading.value=true;if(!background){data.value=undefined;failed.value=false}
 try{const r:any=await request({url:`/metal/work-order/${work}/auto-release`,isContextCurrent:current,quiet:background});if(!current())return false;data.value=r.data;failed.value=false;return true}
 catch{if(current())failed.value=true;return false}finally{if(valid())loading.value=false}
}
useInventoryRefresh(refresh=>readStatus(true,refresh),computed(()=>false),{allowed:canRead,reset:resetAccess},{paused:loading})
watch(()=>props.workOrderId,()=>{resetAccess();void load()},{immediate:true});onBeforeUnmount(()=>{disposed=true;epoch++})
const hasActivity=computed(()=>Boolean(data.value?.event?.status)||Boolean(data.value?.results?.length))
</script><style scoped>.auto-status{margin-top:16px;padding:16px;border:1px solid #e4e7ed;border-radius:10px;background:#fff;min-width:0}.heading{display:flex;gap:12px;align-items:center;flex-wrap:wrap}.scroll{max-width:100%;overflow-x:auto}p{font-size:13px;color:#667085}</style>

<style scoped>.auto-status :deep(th.el-table__cell),.auto-status :deep(td.el-table__cell){text-align:center}</style>
