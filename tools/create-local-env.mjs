import { randomBytes } from 'node:crypto'
import { existsSync, writeFileSync } from 'node:fs'
import bcrypt from '../frontend/node_modules/bcryptjs/index.js'
const envFile = new URL('../.env', import.meta.url)
if (existsSync(envFile)) throw new Error('.env已存在，拒绝覆盖现有配置')
const databasePassword = randomBytes(24).toString('hex')
const adminPassword = randomBytes(12).toString('base64url')
const hash = await bcrypt.hash(adminPassword,12)
writeFileSync(envFile, [
  'MYSQL_USER=heavy_metal', `MYSQL_PASSWORD=${databasePassword}`, 'MYSQL_PORT=3308',
  'MYSQL_URL=jdbc:mysql://127.0.0.1:3308/heavy_metal?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai',
  'REDIS_HOST=127.0.0.1','REDIS_PORT=6380','REDIS_PASSWORD=',
  `TOKEN_SECRET=${randomBytes(32).toString('hex')}`, `ADMIN_PASSWORD_HASH='${hash}'`,
  `ADMIN_LOGIN_PASSWORD=${adminPassword}`, ''
].join('\n'), {mode:0o600})
writeFileSync(new URL('./.db-bootstrap.sql',import.meta.url),
  `CREATE DATABASE IF NOT EXISTS heavy_metal CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\nCREATE USER 'heavy_metal'@'localhost' IDENTIFIED BY '${databasePassword}';\nGRANT ALL PRIVILEGES ON heavy_metal.* TO 'heavy_metal'@'localhost';\n`,{mode:0o600})
console.log('已生成本地.env和临时建库脚本；未输出密码')
