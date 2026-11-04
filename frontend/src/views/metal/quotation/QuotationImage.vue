<template><div class="quotation-image"><el-image v-if="url" :src="url" :preview-src-list="[url]" preview-teleported fit="contain" alt="产品规格尺寸示意图"/><p v-else-if="failed">规格图加载失败 <el-button link @click="load">重试</el-button></p><p v-else>规格图加载中…</p></div></template>
<script setup lang="ts">
import {ref,onMounted,onBeforeUnmount} from 'vue'
import request from '@/utils/request'
const props=defineProps<{quoteId:string;index:number}>(),url=ref(''),failed=ref(false)
let disposed=false,ticket=0
async function load(){const round=++ticket,current=()=>!disposed&&round===ticket;failed.value=false;try{const blob:Blob=await request({url:`/metal/quotation/orders/${props.quoteId}/items/${props.index}/image`,responseType:'blob',isContextCurrent:current});if(!current())return;if(!blob.type.includes('image/png'))throw new Error('图片内容无效');url.value=URL.createObjectURL(blob)}catch{if(current())failed.value=true}}
onMounted(load);onBeforeUnmount(()=>{disposed=true;ticket++;if(url.value)URL.revokeObjectURL(url.value)})
</script>
<style scoped>.quotation-image{min-width:0;text-align:center}.el-image{width:100%;max-width:600px;min-height:180px;max-height:400px}.quotation-image p{color:#909399}</style>
