import bcrypt from '../frontend/node_modules/bcryptjs/index.js'
import readline from 'node:readline'

if (!process.stdin.isTTY) throw new Error('请在交互终端运行此命令，以隐藏输入密码')
process.stdout.write('输入初始管理员密码（至少8位，输入不回显）：')
readline.emitKeypressEvents(process.stdin)
process.stdin.setRawMode(true)
let password = ''
process.stdin.on('keypress', async (text, key) => {
  if (key?.ctrl && key.name === 'c') { process.stdin.setRawMode(false); process.exit(1) }
  if (key?.name === 'backspace') { password = password.slice(0,-1); return }
  if (key?.name === 'return') {
    process.stdin.setRawMode(false); process.stdin.pause(); process.stdout.write('\n')
    if (password.length < 8 || Buffer.byteLength(password,'utf8') > 72) { console.error('密码必须至少8位，且不能超过72个UTF-8字节'); process.exit(1) }
    console.log(await bcrypt.hash(password,12)); password = ''; process.exit(0)
  }
  if (text && !key?.ctrl && !key?.meta) password += text
})
