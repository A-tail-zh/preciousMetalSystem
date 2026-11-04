<template>
 <div v-if="applicable">
  客户来料净重（g）：{{order.customerIncomingWeight==null?'未登记':decimalText(order.customerIncomingWeight)}}
  <el-button v-if="canEdit()" link type="primary" @click="open">更正来料重量</el-button>
  <el-dialog :key="dialogKey" v-model="opened" title="更正来料重量" width="min(520px,94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
   <el-form v-loading="loading" label-position="top" :disabled="loading||busy||stale">
    <el-form-item label="客户来料净重（g）"><BusinessDecimalInput v-model="weight" aria-label="客户来料净重（g）" placeholder="可留空，表示未登记"/></el-form-item>
    <el-form-item label="来料更正原因" required><el-input v-model="reason" aria-label="来料更正原因" type="textarea" maxlength="300" show-word-limit/></el-form-item>
    <p>仅登记本工单客户带来的重量，不影响库存或领料。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false"/>
   </el-form>
   <template #footer><el-button :disabled="busy" @click="opened=false">取消</el-button><el-button type="primary" :loading="busy" :disabled="loading||stale" @click="save">保存更正</el-button></template>
  </el-dialog>
 </div>
</template>
<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue'
import {onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
import {ElMessage} from 'element-plus'
import BusinessDecimalInput from '@/components/BusinessDecimalInput.vue'
import {orderDetail,orderIncomingWeight,type WorkOrder} from '@/api/metal/work-order'
import {useActionPermission} from '@/composables/useActionPermission'
import useUserStore from '@/store/modules/user'
import {getToken} from '@/utils/auth'
import {decimalText} from '@/utils/business-decimal'
import {decimalError,textError} from '@/utils/input-validation'
import {notifyInventoryChanged} from '@/utils/inventory-change'

const props=defineProps<{order:WorkOrder}>(),emit=defineEmits<{changed:[]}>()
const permission=useActionPermission(),user=useUserStore()
const applicable=computed(()=>!(props.order.taskType==='RAW_PROCESSING'&&props.order.processingType==='DIRECT'))
const opened=ref(false),loading=ref(false),busy=ref(false),stale=ref(false),weight=ref(''),reason=ref(''),error=ref(''),dialogKey=ref(0)
let epoch=0,version=0,disposed=false
const canEdit=()=>!disposed&&applicable.value&&!!user.token&&user.token===getToken()&&permission.hasAll(['metal:work-order:query','metal:work-order:edit'])
function reset(){epoch++;opened.value=false;loading.value=false;busy.value=false;weight.value='';reason.value='';error.value='';dialogKey.value++}
async function open(){
 if(!canEdit()||busy.value||!props.order.id)return
 const ticket=++epoch,work=props.order.id,session=user.token,account=String(user.id)
 const current=()=>canEdit()&&ticket===epoch&&work===props.order.id&&session===user.token&&account===String(user.id)
 opened.value=true;loading.value=true;stale.value=false;weight.value='';reason.value='';error.value='';dialogKey.value++
 try{const response=await orderDetail(work,current);if(!current())return;if(response.data.id!==work||response.data.version==null)throw Error('工单资料不完整，请关闭后重新打开');version=response.data.version;weight.value=response.data.customerIncomingWeight??''}
 catch(e:any){if(current()){stale.value=true;error.value=e.message||'加载失败，请关闭后重新打开'}}
 finally{if(current())loading.value=false}
}
async function save(){
 if(!canEdit()||!opened.value||loading.value||busy.value||stale.value||!props.order.id)return
 error.value=decimalError(weight.value,'客户来料净重',{optional:true})||textError(reason.value,'来料更正原因',300,true)
 if(error.value)return
 const ticket=epoch,work=props.order.id,session=user.token,account=String(user.id)
 const current=()=>canEdit()&&ticket===epoch&&work===props.order.id&&session===user.token&&account===String(user.id)
 busy.value=true
 try{await orderIncomingWeight(work,{version,customerIncomingWeight:weight.value===''?null:weight.value,reason:reason.value.trim()},current);if(current()){opened.value=false;ElMessage.success('来料重量已更正');emit('changed');notifyInventoryChanged()}}
 catch(e:any){if(current()){error.value=e.message||'更正失败';if(typeof e.code!=='number'||/版本|刷新|重新加载/.test(error.value)){stale.value=true;error.value+='；请关闭后重新打开核对最新登记'}}}
 finally{if(current())busy.value=false}
}
watch(opened,value=>{if(!value&&!busy.value){epoch++;loading.value=false}})
watch(()=>props.order.id,reset)
watch(()=>props.order.version,value=>{if(opened.value&&!loading.value&&!busy.value&&value!==version){stale.value=true;error.value='工单已被修改，请关闭后重新打开核对；当前输入已保留'}})
permission.onChange(reset)
onBeforeRouteLeave(()=>busy.value?false:undefined);onBeforeRouteUpdate(()=>busy.value?false:undefined)
onBeforeUnmount(()=>{disposed=true;epoch++})
</script>
