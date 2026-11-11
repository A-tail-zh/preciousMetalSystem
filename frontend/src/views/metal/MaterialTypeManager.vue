<template>
  <section class="type-page">
    <el-alert v-if="!categories.length" title="暂无类型查看权限" type="info" :closable="false" />
    <template v-if="categories.length">
      <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="initialize">重新加载</el-button></el-alert>
      <el-alert v-if="uncertain" title="上次提交结果未知，请核实后再继续操作，勿重复提交。" type="warning" :closable="false"><el-button @click="verifyResult" :loading="busy">核实提交结果</el-button></el-alert>
      <div class="filter-toolbar-card type-toolbar">
        <div class="filters">
          <span>所属工厂：{{factoryName}}</span>
          <el-input :disabled="!factoryId||busy" v-model="keyword" :placeholder="`请输入${label}名称`" :aria-label="`${label}名称筛选`" maxlength="100" clearable @keyup.enter="search" />
          <div class="filter-action-group"><el-button class="list-action" :disabled="busy||!factoryId" @click="search">查询</el-button><el-button class="list-action list-action--neutral" :disabled="busy" @click="resetFilters">重置</el-button></div>
        </div>
        <div class="filter-action-group"><el-button v-if="can('add')" class="list-action list-action--create" :disabled="!ready" @click="openEditor()">添加</el-button><el-button v-if="can('edit')" class="list-action list-action--edit" :disabled="!ready||selected.length!==1" @click="openEditor(selected[0])">编辑</el-button><el-button v-if="can('remove')" class="list-action list-action--delete" :disabled="!ready||!selected.length" @click="remove(selected)">删除</el-button></div>
      </div>
      <el-table :key="`${factoryId}-${activeTab}`" ref="table" v-loading="loading" :data="rows" row-key="id" border class="type-table" @selection-change="selected=$event">
        <el-table-column v-if="can('edit')||can('remove')" type="selection" width="44" align="center" />
        <el-table-column label="序号" width="72" align="center"><template #default="{$index}">{{(page-1)*pageSize+$index+1}}</template></el-table-column>
        <el-table-column prop="name" :label="`${label}名称`" min-width="180" />
        <el-table-column v-if="activeTab==='material'" label="物料分类" width="140"><template #default="{row}">{{row.materialCategory||'未设置'}}</template></el-table-column>
        <el-table-column prop="factoryName" label="所属工厂" min-width="180" />
        <el-table-column prop="updatedAt" label="更新时间" width="200" align="center"><template #default="{row}">{{row.updatedAt?.replace('T',' ')}}</template></el-table-column>
        <el-table-column v-if="can('edit')||can('remove')" label="操作" width="160" align="center"><template #default="{row}"><el-button v-if="can('edit')" class="list-action list-action--edit list-action--row" :disabled="!ready" @click="openEditor(row)">编辑</el-button><el-button v-if="can('remove')" class="list-action list-action--delete list-action--row" :disabled="!ready" @click="remove([row])">删除</el-button></template></el-table-column>
        <template #empty><div class="empty-state">{{!factoryId?'请先选择所属工厂':appliedKeyword?'未找到匹配的类型，请调整查询条件':`暂无${label}`}}</div></template>
      </el-table>
      <div class="type-pagination"><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :disabled="busy" :total="total" :page-sizes="[16,32,64]" layout="total, sizes, prev, pager, next, jumper" @change="load" /></div>
    </template>
    <el-dialog v-model="dialogOpen" :title="`${editing?'编辑':'添加'}${label}`" append-to-body width="min(560px, 94vw)" class="base-info-form-dialog" :close-on-click-modal="false" :close-on-press-escape="!busy" :before-close="closeEditor">
      <el-form class="type-editor" label-position="left" label-width="112px" @submit.prevent="save">
        <el-form-item label="所属工厂"><el-input :model-value="factoryName" disabled /></el-form-item>
        <el-form-item :label="`${label}名称`" required :error="formError">
          <el-input v-model="name" :aria-label="`${label}名称`" :placeholder="`请输入${label}名称`" maxlength="100" show-word-limit :disabled="busy||uncertain" />
        </el-form-item>
        <el-form-item v-if="activeTab==='material'" label="物料分类" required :error="categoryError">
          <el-select v-model="materialCategory" @change="categoryError=''" aria-label="物料分类" placeholder="请选择物料分类" :disabled="busy||uncertain"><el-option v-for="category in ['旧料','粉料','块料']" :key="category" :label="category" :value="category" /></el-select>
        </el-form-item>
        <el-button v-if="conflict" @click="reloadEditor">重新加载最新资料</el-button>
        <el-button v-if="uncertain" :loading="busy" @click="verifyResult">核实提交结果</el-button>
      </el-form>
      <template #footer><el-button :disabled="busy||uncertain" @click="closeEditor()">取消</el-button><el-button type="primary" :loading="busy" :disabled="!ready||conflict" @click="save">确定</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts" name="MaterialTypeManager">
import {computed,onMounted,onBeforeUnmount,ref} from 'vue'
import {onBeforeRouteLeave} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import type {TableInstance} from 'element-plus'
import request from '@/utils/request'
import {useActionPermission} from '@/composables/useActionPermission'
import useUserStore from '@/store/modules/user'

type Category='material'
type TypeRow={id:string;factoryId:string;factoryName:string;name:string;version:number;updatedAt:string;materialCategory?:string|null}
const props=defineProps<{factoryId:string;factoryName:string}>()
const emit=defineEmits<{changed:[name?:string]}>()
const permission=useActionPermission(),user=useUserStore()
const allCategories:{key:Category;label:string}[]=[{key:'material',label:'物料类型'}]
const categories=computed(()=>allCategories.filter(c=>permission.has(`metal:${c.key}-type:list`)))
const activeTab=ref<Category>(categories.value[0]?.key??'material'),factoryId=computed(()=>props.factoryId)
const keyword=ref(''),appliedKeyword=ref(''),page=ref(1),pageSize=ref(16),selected=ref<TypeRow[]>([]),table=ref<TableInstance>(),rows=ref<TypeRow[]>([]),total=ref(0)
const loading=ref(false),busy=ref(false),error=ref(''),uncertain=ref(false),conflict=ref(false)
const dialogOpen=ref(false),editing=ref<TypeRow>(),name=ref(''),originalName=ref(''),formError=ref('')
const materialCategory=ref(''),originalCategory=ref(''),categoryError=ref('')
const label=computed(()=>allCategories.find(c=>c.key===activeTab.value)!.label),factoryName=computed(()=>props.factoryName)
const can=(action:string)=>permission.has(`metal:${activeTab.value}-type:${action}`)
const ready=computed(()=>!!factoryId.value&&can('list')&&!loading.value&&!busy.value&&!uncertain.value&&!error.value)
let epoch=0,sequence=0,alive=true
const identity=()=>JSON.stringify([user.token,user.id,user.sessionReady,user.sessionEpoch,user.permissions])
function context(){const round=epoch,account=identity(),factory=factoryId.value,kind=activeTab.value;return()=>alive&&round===epoch&&account===identity()&&factory===factoryId.value&&kind===activeTab.value}
function api(path:string,config:Record<string,unknown>={},current=context()):Promise<any>{return request({url:'/metal/type-management/'+path,quiet:true,isContextCurrent:current,...config} as any) as any}
function clearSelection(){selected.value=[];table.value?.clearSelection()}
async function initialize(){await load()}
async function load(){
  const current=context(),round=++sequence;loading.value=true;error.value='';clearSelection()
  if(!factoryId.value||!can('list')){rows.value=[];total.value=0;loading.value=false;return}
  try{
    const list=await api(activeTab.value+'/list',{params:{factoryId:factoryId.value,name:appliedKeyword.value,pageNum:page.value,pageSize:pageSize.value}},current)
    if(!current()||round!==sequence)return
    rows.value=list.rows;total.value=list.total
    if(page.value>1&&!rows.value.length){page.value=Math.max(1,Math.ceil(total.value/pageSize.value));await load()}
  }catch(e:any){if(current()&&round===sequence){rows.value=[];total.value=0;error.value=e.message||'类型加载失败，请重试'}}finally{if(current()&&round===sequence)loading.value=false}
}
function search(){appliedKeyword.value=keyword.value.trim();page.value=1;void load()}
function resetContext(){epoch++;dialogOpen.value=false;keyword.value='';appliedKeyword.value='';page.value=1;rows.value=[];clearSelection();void load()}
async function confirmLeave(){
  if(busy.value||uncertain.value){ElMessage.warning('请先等待操作完成或核实提交结果');return false}
  if(!dialogOpen.value||(name.value===originalName.value&&materialCategory.value===originalCategory.value))return true
  try{await ElMessageBox.confirm('当前内容尚未保存，确定放弃修改吗？','未保存的修改',{confirmButtonText:'放弃修改',cancelButtonText:'继续编辑',type:'warning'});return true}catch{return false}
}
async function resetFilters(){if(await confirmLeave())resetContext()}
async function closeEditor(done?:()=>void){if(await confirmLeave()){dialogOpen.value=false;done?.()}}
defineExpose({confirmLeave})
onBeforeRouteLeave(confirmLeave)
onBeforeUnmount(()=>{alive=false;epoch++})
permission.onChange(()=>{epoch++;dialogOpen.value=false;busy.value=false;uncertain.value=false;rows.value=[];activeTab.value=categories.value[0]?.key??'material';if(user.sessionReady&&categories.value.length)void initialize()})
onMounted(()=>{if(categories.value.length)void initialize()})
async function openEditor(row?:TypeRow){
  if(!ready.value||!can(row?'edit':'add'))return
  const current=context();busy.value=true
  try{editing.value=row?(await api(activeTab.value+'/'+row.id,{params:{factoryId:factoryId.value}},current)).data:undefined;if(!current())return;name.value=editing.value?.name??'';originalName.value=name.value;materialCategory.value=editing.value?.materialCategory??'';originalCategory.value=materialCategory.value;categoryError.value='';formError.value='';conflict.value=false;dialogOpen.value=true}
  catch(e:any){if(current())ElMessage.error(e.message||'最新资料加载失败')}finally{if(current())busy.value=false}
}
async function reloadEditor(){if(!editing.value||!(await confirmLeave()))return;const row=editing.value;dialogOpen.value=false;await openEditor(row)}
function failure(e:any){formError.value=e.message||'请求失败';const code=Number(e.code??e.response?.data?.code??e.response?.status);if(!code||code>=500){uncertain.value=true;formError.value='提交结果未知，请先核实，勿重复提交'}else if(code===409&&/已变化|已修改/.test(formError.value))conflict.value=true;ElMessage.error(formError.value)}
async function save(){
  if(!ready.value||conflict.value||!can(editing.value?'edit':'add'))return
  const clean=name.value.trim();if(!clean){formError.value='请输入'+label.value+'名称';return}
  if(activeTab.value==='material'&&!['旧料','粉料','块料'].includes(materialCategory.value)){categoryError.value='请选择物料分类';return}
  const current=context();busy.value=true;formError.value='';categoryError.value=''
  try{await api(activeTab.value+(editing.value?'/'+editing.value.id:''),{method:editing.value?'put':'post',headers:{repeatSubmit:false},data:{factoryId:factoryId.value,name:clean,version:editing.value?.version,...(activeTab.value==='material'?{materialCategory:materialCategory.value}:{})}},current);if(!current())return;const added=!editing.value;dialogOpen.value=false;ElMessage.success('保存成功');emit('changed',added?clean:undefined);await load()}
  catch(e:any){if(current())failure(e)}finally{if(current())busy.value=false}
}
async function remove(selection:TypeRow[]){
  if(!ready.value||!can('remove')||!selection.length)return
  const current=context(),targets=[...selection]
  try{await ElMessageBox.confirm(`确定删除选中的 ${targets.length} 个${label.value}吗？`,'删除确认',{type:'warning',confirmButtonText:'删除',cancelButtonText:'取消'})}catch{return}
  if(!current()||!ready.value)return;busy.value=true
  try{await api(activeTab.value+'/batch-delete',{method:'post',headers:{repeatSubmit:false},data:{factoryId:factoryId.value,items:targets.map(r=>({id:r.id,version:r.version}))}},current);if(!current())return;ElMessage.success('删除成功');emit('changed');await load()}
  catch(e:any){if(current())failure(e)}finally{if(current())busy.value=false}
}
async function verifyResult(){
  if(busy.value)return;const current=context();busy.value=true
  try{
    const result=await api(activeTab.value+'/list',{params:{factoryId:factoryId.value,name:dialogOpen.value?name.value.trim():'',pageSize:64}},current)
    if(!current())return
    await ElMessageBox.confirm(dialogOpen.value?`当前库中匹配名称的记录：${result.rows.map((r:TypeRow)=>r.name+'（版本 '+r.version+'）').join('、')||'无'}。关闭编辑并刷新列表，核对后再决定下一步。`:'将刷新当前列表，请核对记录是否仍存在后再决定下一步。','核实提交结果',{confirmButtonText:'关闭并刷新核对',cancelButtonText:'继续保留输入',type:'warning'})
    if(!current())return;uncertain.value=false;dialogOpen.value=false;await load()
  }catch(e:any){if(current()&&e!=='cancel'&&e!=='close')ElMessage.error(e.message||'核实失败，请稍后重试')}finally{if(current())busy.value=false}
}
</script>

<style scoped>
.type-editor :deep(.el-form-item__label){white-space:nowrap;justify-content:flex-start;text-align:left;position:relative;padding-left:12px}
.type-editor :deep(.el-form-item.is-required .el-form-item__label::before){position:absolute;left:0;margin-right:0}
.type-editor :deep(.el-select){width:100%}
.type-page{min-width:0;padding:16px 16px 24px;background:var(--business-canvas);box-sizing:border-box}
.type-tabs :deep(.el-tabs__header){margin:0 0 16px}.type-tabs :deep(.el-tabs__item){font-size:15px}.type-tabs :deep(.el-tabs__nav-wrap::after){height:1px;background:var(--business-border)}
.type-toolbar{display:flex;justify-content:space-between;gap:16px;flex-wrap:wrap;margin-bottom:16px}.filters{display:flex;gap:12px;flex-wrap:wrap;align-items:center}.factory-filter{display:flex;gap:8px;align-items:center}.factory-filter .el-select{width:220px}.filters .el-input{width:240px}
.type-table{width:100%}.type-pagination{margin-top:16px;display:flex;justify-content:flex-end}.empty-state{padding:24px;color:#909399}
</style>
