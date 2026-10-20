<template>
 <section class="master-page" :class="{'material-page':kind==='material','line-page':kind==='line'}">
  <div class="master-toolbar">
<BusinessFilters :kind="kind" :allow-export="kind==='staff'&&can('system:user:export')" :exporting="exporting" @apply="applyFilters" @export="exportRows"><template #actions>
<el-button v-hasPermi="[`${config.permission}:add`]" class="add-button" @click="openAdd">添加</el-button>
   <el-button v-hasPermi="[`${config.permission}:edit`]" class="edit-button" :disabled="selected.length!==1" @click="openRecord(selected[0])">编辑</el-button>
   <el-button v-hasPermi="[`${config.permission}:remove`]" class="delete-button" :disabled="!selected.length||deleting" @click="remove(selected)">删除</el-button>
</template></BusinessFilters>
    </div>
  <div v-if="editLoading" role="status">正在加载编辑资料… <el-button link @click="cancelEdit">取消加载</el-button></div>
  <div v-if="editError" role="alert">编辑资料加载失败 <el-button link @click="retryEdit()">重试</el-button></div>
  <el-table ref="tableRef" v-loading="loading" :data="rows" border :style="{width:fullWidthTable?'100%':`min(100%, ${config.width}px)`}" class="master-table" @selection-change="selected=$event">
   <el-table-column v-if="showActions" type="selection" :width="fullWidthTable?54:36" align="center"/>
   <el-table-column v-for="column in config.columns" :key="column.key" :label="column.label" :width="fullWidthTable?(column.key==='createTime'?168:undefined):column.width" :min-width="fullWidthTable?Math.max(column.width,column.key==='purity'?80:column.key==='volume'?96:0):undefined" align="center">
    <template #default="{row,$index}">
     <template v-if="kind==='process'&&column.key==='id'">{{(query.pageNum-1)*query.pageSize+$index+1}}</template>
     <el-switch v-else-if="column.key==='status'" :model-value="row.status" active-value="0" inactive-value="1" :disabled="!can(`${config.permission}:edit`)||row.id==='1'||statusBusy===row.id" @change="changeStatus(row,$event)"/>
     <template v-else-if="column.key==='productDisplay'">{{row.name}}<br>{{row.category}}</template>
     <template v-else-if="kind==='line' && column.key==='factoryName' && !row.factoryId">未分配工厂</template>
     <template v-else-if="column.key.endsWith('Time')">{{displayDateTime(row[column.key])}}</template>
     <template v-else-if="Array.isArray(row[column.key])"><div v-for="(value,index) in row[column.key]" :key="index">{{value}}</div></template>
     <template v-else>{{row[column.key] ?? ''}}</template>
    </template>
   </el-table-column>
   <el-table-column v-if="showActions" label="操作" :width="fullWidthTable?163:undefined" :min-width="kind==='staff'?200:kind==='product'?118:100" align="center">
    <template #default="{row}">
     <el-button v-if="kind==='product'" v-hasPermi="[`${config.permission}:query`]" class="add-button row-button" @click="openRecord(row,true)">查看</el-button>
     <el-button v-if="kind==='staff'" v-hasPermi="['system:user:resetPwd']" class="add-button row-button" @click="openPassword(row)">重置密码</el-button>
     <el-button v-hasPermi="[`${config.permission}:edit`]" class="edit-button row-button" :disabled="kind==='role'&&row.id==='1'" @click="openRecord(row)">编辑</el-button>
     <el-button v-hasPermi="[`${config.permission}:remove`]" class="delete-button row-button" :disabled="deleting||(['staff','role'].includes(kind)&&row.id==='1')" @click="remove([row])">删除</el-button>
    </template>
   </el-table-column>
  </el-table>
  <div v-if="loadFailed" class="load-error">列表加载失败 <el-button link type="primary" @click="load">重试</el-button></div>
  <div class="master-pagination"><el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" :page-sizes="[16,32,64]" layout="total, sizes, prev, pager, next, jumper" @size-change="sizeChanged" @current-change="load"/></div>
  <el-dialog :class="{'base-info-form-dialog':['product','line','material','process'].includes(kind)}" v-model="dialogOpen" :title="`${readonly?'查看':form.id?'编辑':'添加'}${config.title.replace('管理','')}`" :width="['product','line'].includes(kind)?'min(900px,94vw)':'700px'" :top="kind==='line'?'5vh':undefined" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" >
   <el-form ref="formRef" :model="form" :rules="rules" label-width="160px" :disabled="readonly||saving||rolesLoading">
    <template v-if="['line','product','material'].includes(kind)">
     <el-form-item :label="kind==='line'?'产线编号':kind==='product'?'产品编号':'物料编码'" prop="code"><el-input v-model="form.code" maxlength="64"/></el-form-item>
    </template>
    <el-form-item v-if="['line','product','process'].includes(kind)" :label="kind==='line'?'产线名称':kind==='product'?'产品名称':'工序名称'" prop="name"><el-input v-model="form.name" maxlength="100"/></el-form-item>
    <el-form-item v-if="['line','staff','role'].includes(kind)" label="所属工厂" prop="factoryId"><el-select @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="form.factoryId" :clearable="kind==='line' && isAdmin" :placeholder="kind==='line'&&isAdmin?'未分配工厂（可留空）':'请选择所属工厂'" :disabled="!isAdmin||form.id==='1'" @change="factoryChanged" style="width:100%"><el-option v-for="o in factories" :key="o.id" :value="o.id" :label="o.name"/></el-select></el-form-item>
    <el-form-item v-if="kind==='line'" label="关联产品"><el-select class="line-product-select" @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="form.productIds" multiple filterable style="width:100%"><el-option v-for="o in products" :key="o.id" :value="o.id" :label="o.name"/></el-select></el-form-item>
    <el-form-item v-if="kind==='material'" label="物料类型" prop="materialType"><el-select @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="form.materialType" style="width:100%"><el-option v-for="value in materialTypes" :key="value" :value="value"/></el-select></el-form-item>
    <template v-if="kind==='process'">
     <el-form-item label="工序类型" prop="processType"><el-select @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="form.processType" style="width:100%"><el-option v-for="(name,index) in processTypes" :key="index" :label="name" :value="index+1"/></el-select></el-form-item>
     <el-form-item label="工序描述"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="2000"/></el-form-item>
    </template>
    <el-form-item v-if="kind==='material'" label="规格型号"><el-input v-model="form.specification" maxlength="200"/></el-form-item>
    <template v-if="kind==='product'">
     <el-form-item v-if="readonly" label="规格型号"><el-input class="product-specification-input" :model-value="automaticSpecification" type="textarea" :autosize="{minRows:1}" readonly/></el-form-item>
     <el-form-item label="产品类别" prop="category"><el-select @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="form.category" style="width:100%" @change="categoryChanged"><el-option v-for="(_,category) in productDimensions" :key="category" :value="category"/></el-select></el-form-item>
     <el-form-item v-for="dimension in dimensionFields" :key="dimension.key" :label="`${dimension.label}（cm）`" :prop="`dimensions.${dimension.key}`" :rules="[{validator:positiveDecimal,trigger:'blur'}]"><el-input v-model="form.dimensions![dimension.key]" inputmode="decimal"/></el-form-item>
     <el-form-item label="产品体积（cm³）" prop="volume"><el-input :model-value="readonly?form.volume:automaticVolume" readonly :placeholder="supportedVolumeCategories.includes(form.category??'')?'输入尺寸后自动计算':'该类别暂无计算公式'"/></el-form-item>
     <el-form-item label="纯度（%）" prop="purity"><el-input v-model="form.purity" inputmode="decimal"/></el-form-item>
     <el-form-item label="理论成品净重（g）" prop="weight"><el-input v-model="form.weight" inputmode="decimal"/></el-form-item>
     <el-divider>工序与理论损耗（g）</el-divider>
     <div v-for="(step,index) in form.processes" :key="index" class="process-step">
      <span>{{index+1}}</span><el-select @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="step.processId" filterable placeholder="选择工序"><el-option v-for="o in processes" :key="o.id" :label="o.name" :value="o.id"/></el-select>
      <el-form-item class="loss-field" label-width="0" :prop="`processes.${index}.loss`" :rules="[{validator:lossDecimal,trigger:['blur','change']}]"><el-input v-model="step.loss" placeholder="理论损耗（g）" inputmode="decimal" :aria-label="`第${index+1}道工序理论损耗（g）`"/></el-form-item>
      <el-button :disabled="readonly||saving||index===0" class="step-order-button" @click="moveStep(index,-1)">上移</el-button><el-button :disabled="readonly||saving||index===(form.processes?.length??0)-1" class="step-order-button" @click="moveStep(index,1)">下移</el-button><el-button type="danger" @click="form.processes?.splice(index,1)">移除</el-button>
     </div>
     <el-button v-if="!readonly" @click="form.processes?.push({processId:'',loss:'0'})">添加工序</el-button>
    </template>
    <template v-if="kind==='staff'">
     <el-form-item label="手机号" prop="phonenumber"><el-input v-model="form.phonenumber" maxlength="11"/></el-form-item>
     <el-form-item label="姓名" prop="nickName"><el-input v-model="form.nickName" maxlength="100"/></el-form-item>
     <el-form-item label="角色" prop="roleIds"><el-select @visible-change="resetSelectScroll" fit-input-width :teleported="false" v-model="form.roleIds" multiple :disabled="form.id==='1'" style="width:100%"><el-option v-for="o in roles" :key="o.id" :label="o.name" :value="o.id"/></el-select></el-form-item>
     <el-form-item v-if="!form.id" label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password autocomplete="new-password"/></el-form-item>
    </template>
    <template v-if="kind==='role'">
     <el-form-item label="角色名称" prop="roleName"><el-input v-model="form.roleName" maxlength="100"/></el-form-item>
     <el-form-item label="显示顺序" prop="roleSort"><el-input-number v-model="form.roleSort" :min="0" :precision="0"/></el-form-item>
    </template>
    <el-form-item v-if="['staff','role'].includes(kind)" label="状态"><el-switch v-model="form.status" active-value="0" inactive-value="1" :disabled="form.id==='1'"/></el-form-item>
    <el-form-item v-if="kind==='role'" label="菜单和按钮权限"><div class="role-permission-tree" role="region" aria-label="菜单和按钮权限列表" tabindex="0"><el-tree ref="treeRef" :data="menuTree" node-key="id" :props="{label:'name',children:'children'}" show-checkbox check-strictly default-expand-all @check="checkMenu"/></div></el-form-item>
   </el-form>
   <template #footer><el-button :disabled="saving" @click="dialogOpen=false">{{readonly?'关闭':'取消'}}</el-button><el-button v-if="!readonly" type="primary" :loading="saving" @click="submit">确定</el-button></template>
  </el-dialog>
  <el-dialog v-model="passwordOpen" title="重置密码" width="500px" :close-on-click-modal="false"><el-input v-model="newPassword" type="password" show-password placeholder="请输入至少8位的新密码" autocomplete="new-password"/><template #footer><el-button :disabled="saving" @click="passwordOpen=false">取消</el-button><el-button type="primary" :loading="saving" @click="resetPassword">确定</el-button></template></el-dialog>
 </section>
</template>
<script setup lang="ts">
import {displayDateTime} from '@/utils/display-date-time'
import { resetSelectScroll } from '@/utils/reset-select-scroll'
import {computed,reactive,ref,nextTick,onMounted,watch,onBeforeUnmount} from 'vue'
import request,{download} from '@/utils/request'
import BusinessFilters from '@/components/BusinessFilters/index.vue'
import {productVolume,supportedVolumeCategories} from '@/utils/product-volume'
import {ElMessage,ElMessageBox} from 'element-plus'
import type {FormInstance,FormRules,TreeInstance,TableInstance} from 'element-plus'
import useUserStore from '@/store/modules/user'
import {masterList,masterDetail,masterSave,masterRemove,masterAction,masterOptions} from '@/api/metal/master'
import type {MasterRow,Option} from '@/api/metal/master'
import {masterConfigs,productDimensions,processTypes,materialTypes} from './master-config'
import type {MasterKind} from './master-config'
const props=defineProps<{kind:MasterKind}>(),kind=props.kind,config=masterConfigs[kind]
const fullWidthTable=['line','product','material','process'].includes(kind)
const user=useUserStore(),isAdmin=computed(()=>String(user.id)==='1')
const can=(p:string)=>user.permissions.some((v:string)=>v==='*:*:*'||v===p)
const showActions=computed(()=>can(config.permission+':edit')||can(config.permission+':remove')||(kind==='product'&&can(config.permission+':query'))||(kind==='staff'&&can('system:user:resetPwd')))
const appliedFilters=ref<Record<string,string>>({}),exporting=ref(false)
function applyFilters(filters:Record<string,string>){appliedFilters.value=filters;query.pageNum=1;selected.value=[];tableRef.value?.clearSelection();load()}
async function exportRows(filters:Record<string,string>){if(exporting.value)return;exporting.value=true;try{await download('/system/user/export',filters,'人员.xlsx',{timeout:0})}finally{exporting.value=false}}
const rows=ref<MasterRow[]>([]),selected=ref<MasterRow[]>([]),total=ref(0),loading=ref(false),loadFailed=ref(false),saving=ref(false),deleting=ref(false),statusBusy=ref('')
const query=reactive({pageNum:1,pageSize:16}),dialogOpen=ref(false),readonly=ref(false),passwordOpen=ref(false),newPassword=ref(''),passwordRow=ref<MasterRow>()
const formRef=ref<FormInstance>(),treeRef=ref<TreeInstance>(),tableRef=ref<TableInstance>()
const factories=ref<Option[]>([]),products=ref<Option[]>([]),processes=ref<Option[]>([]),roles=ref<Option[]>([]),menus=ref<Option[]>([])
const blank=():Partial<MasterRow>=>({name:'',code:'',specification:'',description:'',dimensions:{},processes:[],productIds:[],roleIds:[],menuIds:[],status:'0',roleSort:0,volume:null,purity:null,weight:null})
const form=reactive<Partial<MasterRow>>(blank())
const dimensionFields=computed(()=>productDimensions[form.category??'']??[])
const automaticSpecification=computed(()=>dimensionFields.value.filter(d=>form.dimensions?.[d.key]).map(d=>d.label+form.dimensions![d.key]+'cm').join('；'))
const piValue=ref('')
const automaticVolume=computed(()=>productVolume(form.category??'',form.dimensions??{},piValue.value))
watch(automaticVolume,value=>{if(!readonly.value)form.volume=value})
const menuTree=computed(()=>{const indexed=new Map(menus.value.map(m=>[m.id,{...m,children:[] as Option[]}])) ;const roots:Option[]=[];for(const m of indexed.values()){const parent=indexed.get(m.parentId??'');if(parent)parent.children.push(m);else roots.push(m)}return roots})
function checkMenu(node:Option,state:{checkedKeys:(string|number)[]}){
 const checked=state.checkedKeys.some(key=>String(key)===String(node.id))
 const visit=(children:Option[])=>{for(const child of children){treeRef.value?.setChecked(child.id,checked,false);visit(child.children??[])}}
 visit(node.children??[])
}
const decimalOk=(v:unknown,positive=false)=>{const value=String(v??'').trim();return /^(0|[1-9]\d{0,13})(\.\d{1,6})?$/.test(value)&&(!positive||Number(value)>0)}
const positiveDecimal=(_r:unknown,v:unknown,cb:(e?:Error)=>void)=>cb(decimalOk(v,true)?undefined:new Error('尺寸须大于0，最多6位小数'))
const optionalDecimal=(_r:unknown,v:unknown,cb:(e?:Error)=>void)=>cb(v==null||v===''||decimalOk(v)?undefined:new Error('请输入非负数，最多6位小数'))
const lossDecimal=(_r:unknown,v:unknown,cb:(e?:Error)=>void)=>cb(decimalOk(v)?undefined:new Error('请输入非负数，最多14位整数、6位小数'))
const required=(message:string)=>[{required:true,whitespace:true,message,trigger:'blur'}]
const rules:FormRules={code:required('请输入编号或编码'),name:required('请输入名称'),factoryId:[{validator:(_r,v,cb)=>cb(form.id==='1'&&kind==='staff'||kind==='line'&&isAdmin.value||v?undefined:new Error('请选择所属工厂')),trigger:'change'}],category:required('请选择类别'),materialType:required('请选择物料类型'),processType:[{required:true,message:'请选择工序类型',trigger:'change'}],phonenumber:[{required:true,message:'请输入手机号',trigger:'blur'},{pattern:/^1[3-9]\d{9}$/,message:'请输入大陆11位手机号',trigger:'blur'}],nickName:required('请输入姓名'),roleName:required('请输入角色名称'),roleIds:[{type:'array',required:true,min:1,message:'请选择至少一个角色',trigger:'change'}],password:[{required:true,min:8,max:72,message:'密码至少8位，最多72字节',trigger:'blur'}],volume:[{validator:optionalDecimal,trigger:'blur'}],weight:[{validator:optionalDecimal,trigger:'blur'}],purity:[{validator:(_r,v,cb)=>optionalDecimal(_r,v,e=>cb(e??(v!==null&&v!==''&&Number(v)>100?new Error('纯度不能超过100%'):undefined))),trigger:'blur'}]}
let sequence=0
async function load(){const current=++sequence;loading.value=true;try{const data=await masterList(config.endpoint,{...query,...appliedFilters.value});if(current!==sequence)return;rows.value=data.rows;total.value=data.total;selected.value=[];tableRef.value?.clearSelection();loadFailed.value=false}catch{if(current===sequence)loadFailed.value=true}finally{if(current===sequence)loading.value=false}}
function reset(){treeRef.value?.setCheckedKeys([]);for(const key of Object.keys(form))delete(form as Record<string,unknown>)[key];Object.assign(form,blank());formRef.value?.clearValidate()}
function categoryChanged(){form.dimensions=Object.fromEntries(dimensionFields.value.map(d=>[d.key,'']));form.volume=null;form.weight=null}
const editLoading=ref(false),editError=ref(false),rolesLoading=ref(false)
let editEpoch=0,roleEpoch=0,disposed=false,retryEdit:()=>void=()=>{}
function cancelEdit(){if(saving.value)return;++editEpoch;++roleEpoch;editLoading.value=false;rolesLoading.value=false;editError.value=false;dialogOpen.value=false}
watch(dialogOpen,open=>{if(!open&&!saving.value){++editEpoch;++roleEpoch;editLoading.value=false;rolesLoading.value=false}}, {flush:'sync'})
onBeforeUnmount(()=>{disposed=true;++editEpoch;++roleEpoch;++sequence})
async function factoryChanged(){if(kind!=='staff')return;const epoch=editEpoch,current=++roleEpoch,factory=form.factoryId;form.roleIds=[];roles.value=[];rolesLoading.value=true;try{const result=factory?(await masterOptions('roles',{factoryId:factory})).data:[];if(!disposed&&epoch===editEpoch&&current===roleEpoch&&factory===form.factoryId)roles.value=result}finally{if(epoch===editEpoch&&current===roleEpoch)rolesLoading.value=false}}
async function openEditor(row?:MasterRow,view=false){
 if(saving.value||disposed)return;dialogOpen.value=false;const epoch=++editEpoch;++roleEpoch;rolesLoading.value=false;editLoading.value=true;editError.value=false;retryEdit=()=>{void openEditor(row,view)}
 const valid=()=>!disposed&&epoch===editEpoch
 try{
  const draft:Partial<MasterRow>=row?(await masterDetail(config.endpoint,row.id)).data:blank()
  if(!valid())return
  if(!row&&kind==='staff')draft.password='12345678'
  for(const field of ['productIds','roleIds','menuIds'] as const)if(draft[field])draft[field]=draft[field]!.map(String)
  const opts={factories:[] as Option[],products:[] as Option[],processes:[] as Option[],roles:[] as Option[],menus:[] as Option[],pi:''}
  if(!view){
   if(['line','staff','role'].includes(kind)){opts.factories=(await masterOptions('factories')).data;if(!isAdmin.value)draft.factoryId=opts.factories[0]?.id}
   if(kind==='line')opts.products=(await masterOptions('products')).data
   if(kind==='product'){opts.processes=(await masterOptions('processes')).data;opts.pi=(await request({url:'/metal/product/calculation-rule',method:'get'})).data.piValue}
   if(kind==='role')opts.menus=(await masterOptions('menus')).data
   if(kind==='staff'&&draft.factoryId)opts.roles=(await masterOptions('roles',{factoryId:draft.factoryId})).data
  }
  if(view&&kind==='product')opts.processes=(draft.processes??[]).map(s=>({id:s.processId,name:s.processName??s.processId}))
  if(kind==='staff'&&draft.id==='1')opts.roles=[{id:'1',name:'超级管理员'}]
  if(!valid())return
  readonly.value=view;reset();Object.assign(form,draft);factories.value=opts.factories;products.value=opts.products;processes.value=opts.processes;roles.value=opts.roles;menus.value=opts.menus;piValue.value=opts.pi
  dialogOpen.value=true;await nextTick();if(valid()){treeRef.value?.setCheckedKeys(form.menuIds??[]);formRef.value?.clearValidate()}
 }catch{if(valid()){reset();editError.value=true}}finally{if(valid())editLoading.value=false}
}
async function openAdd(){await openEditor()}
async function openRecord(row:MasterRow,view=false){await openEditor(row,view)}
function moveStep(index:number,direction:number){if(readonly.value||saving.value)return;const list=form.processes!;if(index+direction<0||index+direction>=list.length)return;[list[index],list[index+direction]]=[list[index+direction],list[index]]}
async function submit(){if(saving.value||editLoading.value||rolesLoading.value||!dialogOpen.value)return;const epoch=editEpoch;saving.value=true;try{if(!await formRef.value?.validate().catch(()=>false)||epoch!==editEpoch||disposed)return;if(kind==='product'){if(form.dimensions?.outerDiameter&&Number(form.dimensions.outerDiameter)<=Number(form.dimensions.innerDiameter)){ElMessage.error('外径必须大于内径');return}const steps=form.processes??[];if(steps.some(s=>!s.processId||!decimalOk(s.loss))||new Set(steps.map(s=>s.processId)).size!==steps.length){ElMessage.error('请选择不重复工序并填写非负克数损耗');return}}
 const payload=JSON.parse(JSON.stringify(form)) as Partial<MasterRow>;delete payload.createBy;delete payload.createTime;delete payload.updateTime;delete payload.factoryName;delete payload.roleNames;delete payload.productNames;if(kind==='product')delete payload.specification;if(kind==='material')delete payload.name;if(kind==='line'&&!payload.factoryId)payload.factoryId=null;if(kind==='role')payload.menuIds=treeRef.value?.getCheckedKeys().map(String)??[];for(const key of ['volume','purity','weight'] as const)if(payload[key]==='')payload[key]=null;const added=!form.id;await masterSave(config.endpoint,payload);if(disposed||epoch!==editEpoch)return;ElMessage.success(added?'添加成功':'修改成功');dialogOpen.value=false;if(added)query.pageNum=['process','material'].includes(kind)?Math.max(1,Math.ceil((total.value+1)/query.pageSize)):1;await load()}catch{}finally{saving.value=false}}
async function remove(targets:MasterRow[]){if(deleting.value||!targets.length)return;try{await ElMessageBox.confirm(`确认删除选中的${targets.length}条记录？`,'删除确认',{type:'warning',confirmButtonText:'确定',cancelButtonText:'取消'})}catch{return}deleting.value=true;try{await masterRemove(config.endpoint,targets.map(r=>r.id));ElMessage.success('删除成功');query.pageNum=Math.min(query.pageNum,Math.max(1,Math.ceil((total.value-targets.length)/query.pageSize)));await load()}catch{}finally{deleting.value=false}}
async function changeStatus(row:MasterRow,value:unknown){statusBusy.value=row.id;try{await masterAction(config.endpoint,'changeStatus',{id:row.id,version:row.version,status:String(value)});await load()}catch{}finally{statusBusy.value=''}}
function openPassword(row:MasterRow){passwordRow.value=row;newPassword.value='';passwordOpen.value=true}
async function resetPassword(){if(newPassword.value.length<8||new TextEncoder().encode(newPassword.value).length>72){ElMessage.error('密码至少8位，最多72个UTF-8字节');return}if(saving.value)return;saving.value=true;try{await masterAction(config.endpoint,'resetPwd',{id:passwordRow.value!.id,version:passwordRow.value!.version,password:newPassword.value});ElMessage.success('密码重置成功');passwordOpen.value=false;newPassword.value='';await load()}catch{}finally{saving.value=false}}
function sizeChanged(){query.pageNum=1;load()}
onMounted(load)
</script>
<style scoped>
.master-page{min-width:0;max-width:100%;box-sizing:border-box;min-height:calc(var(--design-height,100vh) - 82px);background:#f8fbff;padding:9px 23px 24px 6px;display:flex;flex-direction:column}
.master-page :deep(.el-dialog .el-select-dropdown__wrap){max-height:180px}
.material-page :deep(.el-dialog .el-select-dropdown__wrap){max-height:90px}
.line-page :deep(.el-dialog){min-height:min(740px,90vh);display:flex;flex-direction:column}
.line-page :deep(.el-dialog__body){flex:1}
.line-page :deep(.line-product-select .el-select-dropdown__wrap){max-height:340px}
.master-toolbar{min-width:0;max-width:100%;margin-bottom:16px;flex-shrink:0}.master-toolbar>*{flex-shrink:0}.master-toolbar>.el-button{width:89px;height:35px;margin:0;padding:0;font-size:14px}
.add-button{--el-button-bg-color:#577bef;--el-button-border-color:#577bef;--el-button-text-color:white;--el-button-hover-bg-color:#7795ff;--el-button-hover-text-color:white}
.edit-button{--el-button-bg-color:#ee9b58;--el-button-border-color:#ee9b58;--el-button-text-color:white;--el-button-hover-bg-color:#f8b47f;--el-button-hover-text-color:white}
.delete-button{--el-button-bg-color:#ee6658;--el-button-border-color:#ee6658;--el-button-text-color:white;--el-button-hover-bg-color:#fc887f;--el-button-hover-text-color:white}
.master-toolbar>.el-button:disabled{color:#fff;background:var(--el-button-bg-color);border-color:var(--el-button-border-color);opacity:.6}
.master-table{font-size:14px;--el-table-header-bg-color:#f4f4f4;--el-table-border-color:#e8ebf0;--el-table-header-text-color:#303133}.master-table :deep(th.el-table__cell){height:56px;font-weight:600}.master-table :deep(.cell){font-size:14px;white-space:pre-wrap;overflow-wrap:anywhere;line-height:18px;padding:10px 6px}.master-table :deep(td.el-table__cell){padding:0}.master-table :deep(.row-button){height:25px;font-size:13px;padding:0 9px;margin:2px;border-radius:2px}
.master-pagination{margin-top:auto;padding-top:30px;display:flex;justify-content:center}.master-pagination :deep(.el-pagination){flex-wrap:wrap;justify-content:center}.process-step{display:flex;align-items:flex-start;gap:8px;margin-bottom:12px}.process-step .loss-field{width:160px;flex-shrink:0;margin:0 0 30px}.process-step .el-select{width:230px;flex-shrink:0}.process-step .el-input{width:160px}.load-error{text-align:center;margin:16px;color:#c45656}
.role-permission-tree{width:100%;max-height:min(360px,45vh);overflow:auto;border:1px solid #dcdfe6;border-radius:4px;padding:8px;overscroll-behavior:contain}
.role-permission-tree :deep(.el-tree){min-width:max-content}
.product-specification{white-space:normal;overflow-wrap:anywhere;line-height:24px;padding:4px 0}
.step-order-button.is-disabled{color:#a8abb2;background:#f0f2f5;border-color:#dcdfe6;cursor:not-allowed}
</style>

<style>
.base-info-form-dialog .el-form-item__label{position:relative;justify-content:flex-start;text-align:left;white-space:nowrap;padding-left:12px}
.base-info-form-dialog .el-form-item.is-required:not(.is-no-asterisk)>.el-form-item__label:before{position:absolute;left:0;margin-right:0}
</style>
