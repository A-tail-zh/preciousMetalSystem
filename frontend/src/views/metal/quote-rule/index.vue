<template><section class="quote-page"><el-form ref="ruleForm" :model="form" :rules="rules" scroll-to-error v-loading="loading" label-width="140px" label-position="left" class="rule-form" :disabled="saving||!canEdit">
 <el-form-item label="基础单价" prop="basePrice" required><el-input v-model="form.basePrice" inputmode="decimal" class="price-input"/><span class="price-unit">元</span></el-form-item>
 <el-form-item label="π值" prop="piValue" required><el-input v-model="form.piValue" inputmode="decimal"/></el-form-item>
 <el-form-item><el-button v-if="canEdit" class="save-button" :loading="saving" @click="save">保存配置</el-button></el-form-item>
</el-form></section></template>
<script setup lang="ts">
import type {FormInstance} from 'element-plus'
import {fieldRule,decimalError,applyFieldErrors} from '@/utils/input-validation'
import { useActionPermission } from '@/composables/useActionPermission'
const actionPermission = useActionPermission()
import {reactive,ref,computed,onMounted,onBeforeUnmount} from 'vue'
import {ElMessage} from 'element-plus'
import request from '@/utils/request'
import useUserStore from '@/store/modules/user'
const user=useUserStore(),form=reactive({basePrice:'',piValue:'',version:0}),loading=ref(false),saving=ref(false)
const canEdit=computed(()=>actionPermission.has('metal:quote-rule:edit'))
const ruleForm=ref<FormInstance>()
const rules={basePrice:[fieldRule(v=>decimalError(v,'基础单价',{positive:true}))],piValue:[fieldRule(v=>decimalError(v,'π值',{positive:true,integer:5,fraction:15}))]}
let epoch=0,loadEpoch=0,disposed=false
async function load(){const round=epoch,ticket=++loadEpoch,current=()=>!disposed&&round===epoch&&ticket===loadEpoch;loading.value=true;try{const response=await request({url:'/metal/quote-rule',method:'get',isContextCurrent:current});if(current())Object.assign(form,response.data)}catch{}finally{if(current())loading.value=false}}
async function save(){if(saving.value||loading.value||!canEdit.value)return;const round=epoch;if(!await ruleForm.value?.validate().catch(()=>false)||disposed||round!==epoch||!canEdit.value)return;const current=()=>!disposed&&round===epoch&&canEdit.value,payload={...form};saving.value=true;try{await request({url:'/metal/quote-rule',method:'put',data:payload,isContextCurrent:current});if(!current())return;ElMessage.success('保存成功');await load()}catch(error){if(current())applyFieldErrors(ruleForm.value,error)}finally{if(!disposed&&round===epoch)saving.value=false}}
actionPermission.onChange(()=>{epoch++;loadEpoch++;loading.value=false;saving.value=false;Object.assign(form,{basePrice:'',piValue:'',version:0});ruleForm.value?.clearValidate();if(actionPermission.has('metal:quote-rule:list'))void load()})
onBeforeUnmount(()=>{disposed=true;epoch++;loadEpoch++})
onMounted(load)
</script>
<style scoped>.quote-page{background:#f8fbff;min-height:calc(var(--design-height,100vh) - 82px);padding:35px 30px}.rule-form{width:550px}.price-input{width:100%;flex-shrink:0}.price-unit{position:absolute;left:calc(100% + 8px);white-space:nowrap}.save-button{background:#577bef;border-color:#577bef;color:white}</style>

<style scoped>.rule-form{max-width:100%}.price-input{flex:1;min-width:0;width:auto}.price-unit{position:static;margin-left:8px}.rule-form :deep(.el-form-item__content){flex-wrap:wrap}@media(max-width:600px){.quote-page{padding:16px;box-sizing:border-box}}</style>
