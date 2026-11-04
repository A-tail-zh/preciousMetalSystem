import {workOrderFixture} from './work-order-test-fixture.mjs';
import {warehouseFixture} from './warehouse-test-fixture.mjs';
import {api,ok,detail,login} from './local-test-client.mjs';
import {randomBytes} from 'node:crypto';
export async function uiIdentityFixture({business=false}={}){
 const f=business?await warehouseFixture():await workOrderFixture();
 const menus=ok(await api('/metal/options/menus',f.admin)).data;
 const grant=async(role,perms)=>{const ids=new Set(menus.filter(m=>perms.includes(m.perms)).map(m=>m.id));for(const id of [...ids]){let p=menus.find(m=>m.id===id)?.parentId;while(p&&p!=='0'){ids.add(p);p=menus.find(m=>m.id===p)?.parentId}}ok(await api('/system/role',f.admin,'PUT',{...(await detail('/system/role',role,f.admin)),menuIds:[...ids]}))};
 const base=['metal:work-order:list','metal:work-order:query','metal:inventory:list','metal:inventory:query','metal:warehouse-order:list','metal:warehouse-order:query'];
 await grant(f.roleA,base);await grant(f.roleB,base);
 const accounts=[{phone:f.phoneA,role:'只读查看员',factoryId:f.factoryA},{phone:f.phoneB,role:'只读查看员',factoryId:f.factoryB}];
 if(business){await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'10',quantity:0}]);await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'10',quantity:1}]);await f.post('FINISHED_IN',[{category:'FINISHED',referenceId:f.productA,netWeight:'10',quantity:1}]);}
 else for(let i=0;i<2;i++){const roleId=String(ok(await api('/system/role',f.admin,'POST',{roleName:f.prefix+'展示角色'+i,factoryId:f.factoryA,roleSort:1,status:'0',menuIds:[]})).id);f.made.roles.push(roleId);await grant(roleId,[...base,...(i===0?['metal:warehouse-order:add']:['metal:work-order:add'])]);const phone='132'+randomBytes(4).readUInt32BE().toString().padStart(10,'0').slice(-8);const id=String(ok(await api('/system/user',f.admin,'POST',{phonenumber:phone,nickName:f.prefix+'展示人员'+i,factoryId:f.factoryA,roleIds:[roleId],status:'0',password:f.password})).id);f.made.users.push(id);accounts.push({phone,role:i===0?'仓储录入员':'生产录入员',factoryId:f.factoryA});}
 return {...f,accounts};
}
