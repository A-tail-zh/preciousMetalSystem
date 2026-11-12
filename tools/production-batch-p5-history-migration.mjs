import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';

// Credentials stay in the child environment; neither command arguments nor stdout include them.
const privateFile=process.env.P5_MIGRATION_ENV_FILE;
if(!privateFile)throw new Error('Set P5_MIGRATION_ENV_FILE to the new isolation test-env.private.json');
const env={...process.env,...JSON.parse(fs.readFileSync(privateFile,'utf8'))};
const build=path.resolve('.runtime/production-batch-p5-20261112/migration-build');
fs.mkdirSync(build,{recursive:true});
const cp=[build,path.resolve('backend/ruoyi-metal/target/classes'),path.resolve('backend/ruoyi-common/target/classes'),fs.readFileSync('.runtime/production-batch-p4-20261112/test-classpath.txt','utf8').trim()].join(path.delimiter);
execFileSync('javac',['-encoding','UTF-8','-cp',cp,'-d',build,'tools/ProductionBatchP5HistoryMigration.java','backend/ruoyi-metal/src/main/java/com/ruoyi/metal/service/ProductionBatchService.java'],{env,stdio:'inherit'});
execFileSync('java',['-cp',cp,'com.ruoyi.metal.service.ProductionBatchP5HistoryMigration',...process.argv.slice(2)],{env,stdio:'inherit'});
