const fs=require('node:fs');const path=require('node:path');
module.exports=class ProgressReporter{onTestEnd(test,result){const target=process.env.TEST_PROGRESS_LOG;if(!target)return;fs.mkdirSync(path.dirname(target),{recursive:true});fs.appendFileSync(target,JSON.stringify({title:test.title,file:test.location.file,status:result.status,duration:result.duration})+'\n')}}
