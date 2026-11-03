import {fileURLToPath} from 'node:url';
import {randomUUID} from 'node:crypto';

process.env.UNIFIED_PRODUCTION_SCENARIO='1';
process.env.TEST_RUN_ID??=`unified-production-${Date.now()}-${randomUUID().slice(0,8)}`;
process.env.PROCESSING_ARTIFACT_DIR??=fileURLToPath(new URL(`../.runtime/unified-production-20261103/live-${process.env.TEST_RUN_ID}/`,import.meta.url));
process.env.TEST_FRONTEND_BASE_URL??='http://127.0.0.1:5197';
if(process.env.KEEP_UNIFIED_FIXTURE!==undefined)process.env.KEEP_PROCESSING_FIXTURE=process.env.KEEP_UNIFIED_FIXTURE;
await import('./verify-processing-v2-live.mjs');
