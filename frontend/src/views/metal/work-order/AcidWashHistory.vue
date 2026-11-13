<template><section class="acid-history-page"><template v-if="allowed"><el-button @click="history?.open()">查看历史酸洗记录</el-button><AcidWashDialog ref="history"/></template><p v-else>没有历史酸洗查询权限。</p></section></template>
<script setup lang="ts">
import {computed,onMounted,ref} from 'vue'
import {useActionPermission} from '@/composables/useActionPermission'
import AcidWashDialog from '../warehouse-common/AcidWashDialog.vue'
const permission=useActionPermission(),allowed=computed(()=>permission.hasAny(['metal:warehouse-order:add','metal:warehouse-order:query'])),history=ref<InstanceType<typeof AcidWashDialog>>()
onMounted(()=>{if(allowed.value)void history.value?.open()})
</script>
<style scoped>.acid-history-page{padding:24px}</style>
