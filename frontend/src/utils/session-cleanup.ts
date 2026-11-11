// Release editor holds before invalidating the token on an explicit logout.
const cleanups=new Set<()=>Promise<void>>()
export function registerSessionCleanup(cleanup:()=>Promise<void>){cleanups.add(cleanup);return ()=>cleanups.delete(cleanup)}
export async function runSessionCleanup(){await Promise.all([...cleanups].map(cleanup=>cleanup()))}
