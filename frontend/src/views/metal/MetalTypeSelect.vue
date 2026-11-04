<template>
 <div class="metal-type-control"><el-select aria-label="金属类型" :model-value="modelValue" filterable fit-input-width :disabled="disabled" :loading="loading" placeholder="请选择金属类型" @update:model-value="emit('update:modelValue',String($event))"><el-option v-for="o in options" :key="o.id" :label="o.name" :value="o.id"/></el-select><el-button v-if="allowAdd&&!disabled&&(canAdd||canRemove)" link type="primary" @click="open">添加金属类型</el-button></div>
 <el-dialog v-model="visible" title="添加金属类型" width="min(440px,94vw)" append-to-body :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy"><el-form v-if="canAdd" @submit.prevent="save" label-width="90px"><el-form-item label="金属名称" required :error="error"><el-input v-model="name" maxlength="100" :disabled="busy" @keyup.enter="save"/></el-form-item></el-form><el-table :data="options" class="type-management-table" max-height="260" empty-text="暂无金属类型"><el-table-column prop="name" label="已有金属类型" min-width="140"/><el-table-column v-if="canRemove" label="操作" width="80"><template #default="{row}"><el-button type="danger" size="small" :disabled="busy" @click="remove(row)">删除</el-button></template></el-table-column></el-table><template #footer><el-button :disabled="busy" @click="visible=false">取消</el-button><el-button v-if="canAdd" type="primary" :loading="busy" @click="save">保存</el-button></template></el-dialog>
</template>
<script setup lang="ts">
import {ref,computed,onMounted,onBeforeUnmount,watch} from 'vue'
import request from '@/utils/request'
import {ElMessage,ElMessageBox} from 'element-plus'
import {useActionPermission} from '@/composables/useActionPermission'
import useUserStore from '@/store/modules/user'
import {getToken} from '@/utils/auth'
const props=withDefaults(defineProps<{modelValue?:string;factoryId?:string|null;disabled?:boolean;allowAdd?:boolean}>(),{allowAdd:true})
const emit=defineEmits<{ 'update:modelValue':[string];loaded:[{id:string;name:string}[]];changed:[{id:string;name:string}[]];mutating:[boolean] }>()
const user=useUserStore(),permission=useActionPermission(),canAdd=computed(()=>permission.hasAny(['metal:material:add','metal:material:edit','metal:product:add','metal:product:edit','metal:quote-rule:edit']))
const canRemove=computed(()=>permission.hasAny(['metal:material:remove','metal:product:remove','metal:quote-rule:edit']))
const options=ref<{id:string;name:string}[]>([]),loading=ref(false),visible=ref(false),busy=ref(false),name=ref(''),error=ref('');let epoch=0,disposed=false,draftIdentity=''
watch(busy,value=>emit('mutating',value),{flush:'sync'})
const identity=()=>JSON.stringify([user.sessionReady,user.token,String(user.id),[...user.permissions].sort(),props.factoryId])
const session=()=>!disposed&&user.sessionReady&&!!user.token&&user.token===getToken()
async function load(){if(!props.factoryId||!session())return;const round=++epoch,account=identity(),factory=props.factoryId;loading.value=true;try{const r:any=await request({url:'/metal/metal-types',params:{factoryId:factory},isContextCurrent:()=>session()&&account===identity()&&round===epoch&&factory===props.factoryId});if(session()&&account===identity()&&round===epoch){options.value=r.data;emit('loaded',r.data)}}catch{}finally{if(round===epoch)loading.value=false}}
function open(){if(!props.factoryId||!session())return;draftIdentity=identity();name.value='';error.value='';visible.value=true;void load()}
async function save(){
 if(busy.value||!canAdd.value||!props.factoryId||!session()||draftIdentity!==identity())return
 if(!name.value.trim()){error.value='请输入金属名称';return}
 let round=epoch
 const account=draftIdentity,current=()=>session()&&account===identity()&&round===epoch&&canAdd.value
 busy.value=true
 try{
  const r:any=await request({url:'/metal/metal-types',method:'post',data:{name:name.value,factoryId:props.factoryId},isContextCurrent:current})
  if(!current())return
  visible.value=false
  const refresh=load();round=epoch
  await refresh
  if(current()){emit('changed',options.value);emit('update:modelValue',String(r.id))}
 }catch(e:any){if(current())error.value=e.message||'保存失败，请重试'}
 finally{if(!disposed&&round===epoch)busy.value=false}
}
async function remove(row:{id:string;name:string}){
 if(busy.value||!visible.value||!canRemove.value||!session())return
 const round=epoch,account=identity(),valid=()=>session()&&account===identity()&&round===epoch&&visible.value&&canRemove.value
 busy.value=true
 try{
  await ElMessageBox.confirm(`确定删除金属类型“${row.name}”吗？`,'删除金属类型',{type:'warning',confirmButtonText:'删除',cancelButtonText:'取消'})
  if(!valid())return
  await request({url:`/metal/metal-types/${row.id}`,method:'delete',isContextCurrent:valid})
  if(!valid())return
  ++epoch;options.value=options.value.filter(o=>o.id!==row.id);emit('loaded',options.value);emit('changed',options.value)
  if(props.modelValue===row.id)emit('update:modelValue','')
  ElMessage.success('金属类型已删除');void load()
 }catch{}finally{if(!disposed&&account===identity())busy.value=false}
}
permission.onChange(()=>{epoch++;visible.value=false;busy.value=false;options.value=[];if(permission.hasAny(['metal:material:list','metal:material:query','metal:material:add','metal:material:edit','metal:material:remove','metal:product:list','metal:product:query','metal:product:add','metal:product:edit','metal:product:remove','metal:quote-rule:list','metal:quote-rule:edit','metal:quotation:list']))void load()})
watch(()=>props.factoryId,()=>{epoch++;visible.value=false;busy.value=false;loading.value=false;options.value=[];emit('loaded',[]);void load()})
onMounted(load);onBeforeUnmount(()=>{disposed=true;epoch++;emit('mutating',false)})
</script>
<style scoped>.metal-type-control{display:flex;align-items:center;gap:12px;width:100%;min-width:0}.metal-type-control .el-select{flex:1;min-width:0}.metal-type-control .el-button{flex-shrink:0}.type-management-table{margin-top:16px;width:100%}@media(max-width:600px){.metal-type-control{flex-wrap:wrap}.metal-type-control .el-select{flex-basis:100%;width:100%}}</style>
