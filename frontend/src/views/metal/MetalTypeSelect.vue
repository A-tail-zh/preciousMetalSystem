<template>
 <div class="metal-type-control"><el-select aria-label="金属类型" :model-value="modelValue" filterable fit-input-width :disabled="disabled" :loading="loading" placeholder="请选择金属类型" @update:model-value="emit('update:modelValue',String($event))"><el-option v-for="o in options" :key="o.id" :label="o.name" :value="o.id"/></el-select><el-button v-if="allowAdd&&!disabled&&canAdd" link type="primary" @click="open">添加金属类型</el-button></div>
 <el-dialog v-model="visible" title="添加金属类型" width="min(440px,94vw)" append-to-body :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy"><el-form @submit.prevent="save" label-width="90px"><el-form-item label="金属名称" required :error="error"><el-input v-model="name" maxlength="100" :disabled="busy" @keyup.enter="save"/></el-form-item></el-form><template #footer><el-button :disabled="busy" @click="visible=false">取消</el-button><el-button type="primary" :loading="busy" @click="save">保存</el-button></template></el-dialog>
</template>
<script setup lang="ts">
import {ref,computed,onMounted,onBeforeUnmount} from 'vue'
import request from '@/utils/request'
import {useActionPermission} from '@/composables/useActionPermission'
const props=withDefaults(defineProps<{modelValue?:string;disabled?:boolean;allowAdd?:boolean}>(),{allowAdd:true})
const emit=defineEmits<{ 'update:modelValue':[string];loaded:[{id:string;name:string}[]] }>()
const permission=useActionPermission(),canAdd=computed(()=>permission.hasAny(['metal:material:add','metal:material:edit','metal:product:add','metal:product:edit','metal:quote-rule:edit']))
const options=ref<{id:string;name:string}[]>([]),loading=ref(false),visible=ref(false),busy=ref(false),name=ref(''),error=ref('');let epoch=0,disposed=false
async function load(){const round=++epoch;loading.value=true;try{const r:any=await request({url:'/metal/metal-types',isContextCurrent:()=>!disposed&&round===epoch});if(!disposed&&round===epoch){options.value=r.data;emit('loaded',r.data)}}catch(error){if(!disposed&&round===epoch)throw error}finally{if(round===epoch)loading.value=false}}
function open(){name.value='';error.value='';visible.value=true}
async function save(){
 if(busy.value||!canAdd.value)return
 if(!name.value.trim()){error.value='请输入金属名称';return}
 let round=epoch
 const current=()=>!disposed&&round===epoch&&canAdd.value
 busy.value=true
 try{
  const r:any=await request({url:'/metal/metal-types',method:'post',data:{name:name.value},isContextCurrent:current})
  if(!current())return
  visible.value=false
  const refresh=load();round=epoch
  await refresh
  if(current())emit('update:modelValue',String(r.id))
 }catch(e:any){if(current())error.value=e.message||'保存失败，请重试'}
 finally{if(!disposed&&round===epoch)busy.value=false}
}
permission.onChange(()=>{epoch++;visible.value=false;busy.value=false;options.value=[];if(permission.hasAny(['metal:material:list','metal:material:query','metal:material:add','metal:material:edit','metal:product:list','metal:product:query','metal:product:add','metal:product:edit','metal:quote-rule:list','metal:quote-rule:edit','metal:quotation:list']))void load()})
onMounted(load);onBeforeUnmount(()=>{disposed=true;epoch++})
</script>
<style scoped>.metal-type-control{display:flex;align-items:center;gap:12px;width:100%;min-width:0}.metal-type-control .el-select{flex:1;min-width:0}.metal-type-control .el-button{flex-shrink:0}@media(max-width:600px){.metal-type-control{flex-wrap:wrap}.metal-type-control .el-select{flex-basis:100%;width:100%}}</style>
