import {env} from './local-test-client.mjs';
const tables={factories:['metal_factory','factory_id','factory_name'],lines:['metal_production_line','production_line_id','production_line_name'],products:['metal_product','product_id','product_name'],processes:['metal_process','process_id','process_name'],roles:['sys_role','role_id','role_name'],users:['sys_user','user_id','nick_name'],materials:['metal_material','material_id','material_code']};
export const fixtureEnvironment=()=>({schema:process.env.TEST_DB_SCHEMA||'heavy_metal',port:String(process.env.TEST_MYSQL_PORT||env.MYSQL_PORT),api:process.env.TEST_API_BASE_URL||'http://127.0.0.1:8080'});
export function assertEnvironment(saved,query){const current=fixtureEnvironment();if(!saved||Object.keys(current).some(k=>saved[k]!==current[k]))throw Error('fixture environment mismatch');if(query('SELECT DATABASE()')!==current.schema||query('SELECT @@port')!==current.port)throw Error('database identity mismatch');}
export function assertFixtureOwnership(prefix,made,query){
 if(!/^工单验收_\d+_[a-f0-9]{6}$/.test(prefix))throw Error('invalid fixture prefix');
 const suffix=prefix.slice('工单验收_'.length);
 for(const [group,ids]of Object.entries(made)){if(!tables[group]||!Array.isArray(ids)||ids.some(id=>!/^\d+$/.test(String(id))))throw Error('invalid registry resource');const [table,key,name]=tables[group];for(const id of ids){const label=query(`SELECT ${name} FROM ${table} WHERE ${key}=${id}`);if(label&&(group==='materials'?!label.endsWith(suffix):!label.startsWith(prefix)))throw Error('fixture ownership mismatch: '+group+'/'+id);}}
}
export function assertWarehouseOwnership(f,query){assertFixtureOwnership(f.prefix,{factories:[f.factoryA,f.factoryB]},query)}

export function discoverFixtureResources(prefix,made,query){
 if(!/^工单验收_\d+_[a-f0-9]{6}$/.test(prefix))throw Error('invalid fixture prefix');const suffix=prefix.slice('工单验收_'.length);
 for(const[group,[table,key,name]]of Object.entries(tables)){const value=group==='materials'?suffix:prefix;const expression=group==='materials'?'RIGHT':'LEFT';const result=query(`SELECT ${key} FROM ${table} WHERE ${expression}(${name},CHAR_LENGTH('${value}'))='${value}'`);if(!made[group])made[group]=[];for(const id of result.split(/\r?\n/).filter(Boolean)){if(!/^\d+$/.test(id))throw Error('invalid discovered ID');if(!made[group].includes(id))made[group].push(id);}}
}
