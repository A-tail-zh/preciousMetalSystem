import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import assert from 'node:assert/strict';

const privateFile=process.env.P5_FULL_PROVENANCE_ENV_FILE;
assert(privateFile,'Explicit P5_FULL_PROVENANCE_ENV_FILE required');
const env={...process.env,...JSON.parse(fs.readFileSync(privateFile,'utf8'))};
const build=path.resolve('.runtime/production-batch-p5-full-history-20261112/provenance-build');
fs.mkdirSync(build,{recursive:true});
const cp=[build,path.resolve('backend/ruoyi-metal/target/classes'),path.resolve('backend/ruoyi-common/target/classes'),fs.readFileSync('.runtime/production-batch-p4-20261112/test-classpath.txt','utf8').trim()].join(path.delimiter);
execFileSync('javac',['-encoding','UTF-8','-cp',cp,'-d',build,'tools/ProductionBatchP5FullProvenance.java'],{env,stdio:'inherit'});
execFileSync('java',['-cp',cp,'com.ruoyi.metal.service.ProductionBatchP5FullProvenance',...process.argv.slice(2)],{env,stdio:'inherit'});
