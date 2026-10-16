<template><section class="quote-page"><el-form v-loading="loading" label-width="140px" label-position="left" class="rule-form" :disabled="saving||!canEdit">
 <el-form-item label="基础单价"><el-input v-model="form.basePrice" inputmode="decimal" class="price-input"/><span class="price-unit">元</span></el-form-item>
 <el-form-item label="π值"><el-input v-model="form.piValue" inputmode="decimal"/></el-form-item>
 <el-form-item><el-button v-if="canEdit" class="save-button" :loading="saving" @click="save">保存配置</el-button></el-form-item>
</el-form></section></template>
<script setup lang="ts">
import {reactive,ref,computed,onMounted} from 'vue'
import {ElMessage} from 'element-plus'
import request from '@/utils/request'
import useUserStore from '@/store/modules/user'
const user=useUserStore(),form=reactive({basePrice:'',piValue:'',version:0}),loading=ref(false),saving=ref(false)
const canEdit=computed(()=>user.permissions.some((p:string)=>p==='*:*:*'||p==='metal:quote-rule:edit'))
async function load(){loading.value=true;try{Object.assign(form,(await request({url:'/metal/quote-rule',method:'get'})).data)}finally{loading.value=false}}
async function save(){if(!/^(0|[1-9]\d{0,13})(\.\d{1,6})?$/.test(form.basePrice)||Number(form.basePrice)<=0||!/^(0|[1-9]\d{0,4})(\.\d{1,15})?$/.test(form.piValue)||Number(form.piValue)<=0){ElMessage.error('请填写正数，基础单价最多6位小数，π最多15位小数');return}saving.value=true;try{await request({url:'/metal/quote-rule',method:'put',data:form});ElMessage.success('保存成功');await load()}catch{}finally{saving.value=false}}
onMounted(load)
</script>
<style scoped>.quote-page{background:#f8fbff;min-height:calc(var(--design-height,100vh) - 82px);padding:35px 30px}.rule-form{width:550px}.price-input{width:100%;flex-shrink:0}.price-unit{position:absolute;left:calc(100% + 8px);white-space:nowrap}.save-button{background:#577bef;border-color:#577bef;color:white}</style>
