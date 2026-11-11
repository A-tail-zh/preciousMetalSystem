<template>
  <section class="storage-page storage-warehouses">
    <FactoryToolbar :before-change="confirmDiscard" :disabled="operation.busy.value||!!operation.pending.value">
      <el-input v-model="keyword" class="warehouse-filter" aria-label="仓库名称筛选" placeholder="请输入仓库名称" maxlength="100" clearable :disabled="!state.factoryId" @keyup.enter="search" />
      <el-select v-model="status" class="warehouse-status" aria-label="仓库状态筛选" placeholder="全部状态" clearable :disabled="!state.factoryId">
        <el-option label="启用" value="0" /><el-option label="停用" value="1" />
      </el-select>
      <el-button class="list-action" :disabled="!state.factoryId" @click="search">查询</el-button>
      <el-button class="list-action list-action--neutral" @click="resetFilters">重置</el-button>
      <el-button v-if="can('metal:warehouse:add')" class="list-action list-action--create" :disabled="!state.factoryId" @click="openEditor()">添加</el-button>
      <el-button v-if="can('metal:warehouse:edit')" class="list-action list-action--edit" :disabled="selected.length !== 1" @click="openEditor(selected[0])">编辑</el-button>
      <el-button v-if="can('metal:warehouse:remove')" class="list-action list-action--delete" :disabled="!selected.length" @click="remove(selected)">删除</el-button>
    </FactoryToolbar><OperationStatus :operation="operation"/><el-alert v-if="loadError" :title="loadError" type="error" :closable="false"/>
    <el-table v-loading="loading" ref="table" :data="pageRows" row-key="id" border @selection-change="selected = $event">
      <el-table-column v-if="can('metal:warehouse:edit') || can('metal:warehouse:remove')" type="selection" width="44" />
      <el-table-column label="序号" width="72"><template #default="{ $index }">{{ (page - 1) * pageSize + $index + 1 }}</template></el-table-column>
      <el-table-column label="仓库名称" prop="name" min-width="180" />
      <el-table-column label="所属工厂" min-width="180"><template #default="{ row }">{{ factoryName(row.factoryId) }}</template></el-table-column>
      <el-table-column label="显示顺序" prop="sortOrder" width="110" />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="String(row.status) === '0' ? 'success' : 'info'">{{ String(row.status) === '0' ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="更新时间" prop="updateTime" min-width="180" />
      <el-table-column v-if="can('metal:warehouse:edit') || can('metal:warehouse:remove')" label="操作" width="150">
        <template #default="{ row }">
          <el-button v-if="can('metal:warehouse:edit')" class="list-action list-action--edit list-action--row" @click="openEditor(row)">编辑</el-button>
          <el-button v-if="can('metal:warehouse:remove')" class="list-action list-action--delete list-action--row" @click="remove([row])">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>{{ state.factoryId ? '暂无仓库' : '请选择所属工厂' }}</template>
    </el-table>
    <div class="storage-pagination"><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[16, 32, 64]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="page = 1" @change="load" /></div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑仓库' : '添加仓库'" class="storage-dialog" width="min(560px,94vw)" :close-on-click-modal="false" :before-close="closeEditor" destroy-on-close>
      <OperationStatus :operation="operation"/><el-form :disabled="operation.busy.value||!!operation.pending.value" label-position="left" label-width="112px" @submit.prevent="save">
        <el-form-item label="所属工厂"><el-input :model-value="factoryName(form.factoryId)" aria-label="所属工厂" readonly /></el-form-item>
        <el-form-item label="仓库名称" required :error="nameError"><el-input v-model="form.name" aria-label="仓库名称" maxlength="100" show-word-limit placeholder="请输入仓库名称" /></el-form-item>
        <el-form-item label="显示顺序" required :error="sortError"><el-input-number v-model="form.sortOrder" aria-label="显示顺序" :min="0" :max="2147483647" :step="1" :precision="0" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="form.status" aria-label="仓库状态"><el-radio value="0">启用</el-radio><el-radio value="1">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="operation.busy.value||!!operation.pending.value" @click="closeEditor()">取消</el-button><el-button type="primary" :loading="operation.busy.value" :disabled="!!operation.pending.value||!can(form.id ? 'metal:warehouse:edit' : 'metal:warehouse:add')" @click="save">确认</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts" name="StorageWarehouses">
import {ref,watch,onBeforeUnmount} from 'vue'
import {onBeforeRouteLeave} from 'vue-router'
import {ElMessage,ElMessageBox,type TableInstance} from 'element-plus'
import request from '@/utils/request'
import FactoryToolbar from '../storage-live/FactoryToolbar.vue'
import OperationStatus from '../storage-live/OperationStatus.vue'
import {useStorage,useOperation,message,type Row} from '../storage-live/client'
const {state,can,context}=useStorage(),base='/metal/storage-warehouses'
const keyword=ref(''),status=ref(''),page=ref(1),pageSize=ref(16),total=ref(0),pageRows=ref<Row[]>([]),selected=ref<Row[]>([]),table=ref<TableInstance>(),loading=ref(false),loadError=ref('')
const dialog=ref(false),nameError=ref(''),sortError=ref('')
const blank=()=>({id:'',factoryId:state.factoryId,name:'',sortOrder:0 as number|undefined,status:'0',version:0})
const form=ref(blank());let initial='',epoch=0,alive=true
const factoryName=(id:string)=>state.factories.find(f=>f.id===id)?.name||'—'
const operation=useOperation(base,context,async()=>{dialog.value=false;ElMessage.success('已保存');await load()})
async function load(){selected.value=[];const current=context(),id=++epoch;if(!state.factoryId){pageRows.value=[];total.value=0;return}loading.value=true;loadError.value='';try{const r:any=await request({url:base+'/list',params:{factoryId:state.factoryId,name:keyword.value.trim(),status:status.value,pageNum:page.value,pageSize:pageSize.value},quiet:true});if(alive&&current===context()&&id===epoch){pageRows.value=r.rows||[];total.value=r.total||0}}catch(e){if(current===context()&&id===epoch)loadError.value=message(e)}finally{if(id===epoch)loading.value=false}}
function search(){page.value=1;void load()}function resetFilters(){keyword.value='';status.value='';search()}
async function openEditor(row?:Row){if(!state.factoryId||!can(row?'metal:warehouse:edit':'metal:warehouse:add')||operation.pending.value)return;operation.clear();form.value=blank();if(row){const current=context();try{const r:any=await request({url:base+'/'+row.id,params:{factoryId:state.factoryId}});if(current!==context())return;form.value={...r.data,status:String(r.data.status)}}catch{return}}initial=JSON.stringify(form.value);nameError.value='';sortError.value='';dialog.value=true}
async function confirmDiscard(){if(operation.busy.value||operation.pending.value)return false;const current=context();if(dialog.value&&JSON.stringify(form.value)!==initial){try{await ElMessageBox.confirm('放弃未保存的仓库资料？','确认关闭',{confirmButtonText:'放弃',cancelButtonText:'继续填写'})}catch{return false}}if(current!==context())return false;dialog.value=false;return true}
async function closeEditor(done?:()=>void){if(await confirmDiscard())done?.()}
async function save(){nameError.value='';sortError.value='';if(!form.value.name.trim()||form.value.name.trim().length>100){nameError.value='请输入1至100字的仓库名称';return}if(!Number.isInteger(form.value.sortOrder)||Number(form.value.sortOrder)<0){sortError.value='显示顺序须为非负整数';return}await operation.send(base+(form.value.id?'/'+form.value.id:''),{...form.value,name:form.value.name.trim()},form.value.id?'put':'post')}
async function remove(rows:Row[]){if(!rows.length||operation.pending.value||!can('metal:warehouse:remove'))return;const current=context();try{await ElMessageBox.confirm('删除所选仓库？有库存或业务引用的仓库不能删除。','确认删除',{type:'warning'})}catch{return}if(current===context())await operation.send(base+'/batch-delete',{factoryId:state.factoryId,rows:rows.map(r=>({id:r.id,version:r.version}))})}
watch(()=>state.factoryId,()=>{keyword.value='';status.value='';page.value=1;dialog.value=false;void load()},{immediate:true})
onBeforeRouteLeave(confirmDiscard);onBeforeUnmount(()=>{alive=false;epoch++})
</script>

<style scoped>
.warehouse-filter { width: 220px; }
.warehouse-status { width: 140px; }
:deep(.el-form-item__label) { position: relative; text-align: left; }
:deep(.el-form-item.is-required .el-form-item__label::before) { position: absolute; left: -12px; margin: 0; }
:deep(.el-form-item) { padding-left: 12px; }
:deep(.el-input-number) { width: 100%; }
:deep(.el-input-number .el-input__inner) { text-align: left; }
</style>
