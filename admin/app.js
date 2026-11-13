const $ = id => document.getElementById(id)
let token = sessionStorage.getItem('admin_token') || ''
let me = null
let tab = 'identities'
const escapeHtml = value => String(value == null ? '' : value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))
const can = permission => me && me.permissions.includes(permission)
async function api(path, options = {}) {
  const response = await fetch('/admin/api' + path, { ...options, headers: { 'Content-Type': 'application/json', ...(token ? {Authorization: 'Bearer ' + token} : {}), ...(options.headers || {}) } })
  const data = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(data.message || '请求失败')
  return data
}
function message(value) { $('message').textContent = value }
async function boot() {
  if (!token) return
  try { me = await api('/me'); $('login').hidden = true; $('workspace').hidden = false; $('logout').hidden = false; $('who').textContent = me.username + ' · ' + me.roles.join(', '); await render() }
  catch { token = ''; sessionStorage.removeItem('admin_token') }
}
$('loginForm').onsubmit = async event => {
  event.preventDefault()
  const form = new FormData(event.target)
  try { const result = await api('/login', {method:'POST',body:JSON.stringify(Object.fromEntries(form))}); token = result.token; sessionStorage.setItem('admin_token', token); await boot() }
  catch (error) { alert(error.message) }
}
$('logout').onclick = async () => { try { await api('/logout', {method:'POST'}) } catch {} token=''; me=null; sessionStorage.removeItem('admin_token'); location.reload() }
async function render() {
  const tabs = [['identities','认证审核','identity:read'],['users','用户','users:read'],['admins','管理员','admins:read'],['roles','角色权限','admins:read'],['audit','审计日志','audit:read']]
  const available = tabs.filter(item => can(item[2])); if (!available.some(item => item[0] === tab)) tab = available[0]?.[0]
  $('nav').innerHTML = available.map(([id,label]) => `<button data-tab="${id}" class="${tab===id?'active':'secondary'}">${label}</button>`).join('')
  $('nav').querySelectorAll('button').forEach(button => button.onclick = () => { tab=button.dataset.tab; message(''); render() })
  try { await ({identities,users,admins,roles,audit})[tab]() } catch (error) { message(error.message) }
}
async function identities() {
  const rows=await api('/identities')
  $('content').innerHTML=`<div class="panel"><h2>实名认证与 Coser 审核</h2><table><thead><tr><th>用户</th><th>类型</th><th>证件</th><th>状态</th><th>原因</th><th>操作</th></tr></thead><tbody>${rows.map(row=>`<tr><td>${escapeHtml(row.nickname)}<br><small>${escapeHtml(row.userId)}</small></td><td>${row.mode==='coser'?'Coser':'用户'}</td><td>${escapeHtml(row.maskedId)}</td><td>${escapeHtml(row.status)}</td><td>${escapeHtml(row.reason)}</td><td><div class="actions">${row.status==='pending'&&can('identity:review')?`<button data-review="${escapeHtml(row.id)}" data-decision="verified">通过</button><button class="danger" data-review="${escapeHtml(row.id)}" data-decision="rejected">驳回</button>`:''}</div></td></tr>`).join('')}</tbody></table></div>`
  document.querySelectorAll('[data-review]').forEach(button=>button.onclick=async()=>{
    const decision=button.dataset.decision, reason=decision==='rejected'?prompt('请输入驳回原因')||'':''
    if(decision==='rejected'&&!reason) return
    if(decision==='verified'&&!confirm('确认通过该 Coser 申请？')) return
    try { await api('/identities/'+encodeURIComponent(button.dataset.review),{method:'PATCH',body:JSON.stringify({decision,reason})}); message('审核已保存'); await identities() } catch(error){message(error.message)}
  })
}
async function users() {
  const rows=await api('/users')
  $('content').innerHTML=`<div class="panel"><h2>用户</h2><table><tr><th>ID</th><th>昵称</th><th>角色</th><th>注册时间</th></tr>${rows.map(row=>`<tr><td>${escapeHtml(row.id)}</td><td>${escapeHtml(row.nickname)}</td><td>${escapeHtml(row.role)}</td><td>${escapeHtml(row.createdAt)}</td></tr>`).join('')}</table></div>`
}
async function admins() {
  const [rows,roles]=await Promise.all([api('/admins'),api('/roles')])
  $('content').innerHTML=`${can('admins:write')?`<div class="panel"><h2>新建管理员</h2><form id="createAdmin" class="toolbar"><input name="username" placeholder="用户名（至少3位）" required><input name="password" type="password" placeholder="密码（至少12位）" minlength="12" required><select name="role">${roles.map(role=>`<option value="${escapeHtml(role.id)}">${escapeHtml(role.name)}</option>`).join('')}</select><button>创建</button></form></div>`:''}<div class="panel"><h2>管理员</h2><table><tr><th>用户名</th><th>状态</th><th>操作</th></tr>${rows.map(row=>`<tr><td>${escapeHtml(row.username)}</td><td>${row.enabled?'启用':'禁用'}</td><td class="actions">${can('admins:write')&&row.id!==me.id?`<select data-role="${escapeHtml(row.id)}"><option value="">修改角色</option>${roles.map(role=>`<option value="${escapeHtml(role.id)}">${escapeHtml(role.name)}</option>`).join('')}</select><button class="secondary" data-toggle="${escapeHtml(row.id)}" data-enabled="${row.enabled}">${row.enabled?'禁用':'启用'}</button><button class="secondary" data-reset="${escapeHtml(row.id)}">重置密码</button>`:''}</td></tr>`).join('')}</table></div>`
  if($('createAdmin')) $('createAdmin').onsubmit=async event=>{event.preventDefault();try{await api('/admins',{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData(event.target)))});message('管理员已创建');await admins()}catch(error){message(error.message)}}
  document.querySelectorAll('[data-role]').forEach(select=>select.onchange=async()=>{if(!select.value)return;try{await api('/admins/'+encodeURIComponent(select.dataset.role),{method:'PATCH',body:JSON.stringify({role:select.value})});message('角色已更新');await admins()}catch(error){message(error.message)}})
  document.querySelectorAll('[data-toggle]').forEach(button=>button.onclick=async()=>{try{await api('/admins/'+encodeURIComponent(button.dataset.toggle),{method:'PATCH',body:JSON.stringify({enabled:button.dataset.enabled!=='true'})});message('状态已更新');await admins()}catch(error){message(error.message)}})
  document.querySelectorAll('[data-reset]').forEach(button=>button.onclick=async()=>{const password=prompt('输入至少12位的新密码');if(!password)return;try{await api('/admins/'+encodeURIComponent(button.dataset.reset),{method:'PATCH',body:JSON.stringify({password})});message('密码已重置，该管理员需要重新登录')}catch(error){message(error.message)}})
}
async function roles() {
  const [rows,permissions]=await Promise.all([api('/roles'),api('/permissions')])
  $('content').innerHTML=`${can('admins:write')?'<div class="panel"><h2>新建角色</h2><form id="createRole" class="toolbar"><input name="id" placeholder="英文标识，如 support" required><input name="name" placeholder="显示名称" required><button>创建</button></form></div>':''}${rows.map(role=>`<div class="panel"><h2>${escapeHtml(role.name)} <small>${escapeHtml(role.id)}</small></h2><div class="checklist">${permissions.map(permission=>`<label><input type="checkbox" data-for="${escapeHtml(role.id)}" value="${escapeHtml(permission.code)}" ${role.permissions.includes(permission.code)?'checked':''} ${!can('admins:write')||role.id==='super_admin'?'disabled':''}>${escapeHtml(permission.description)}</label>`).join('')}</div>${can('admins:write')&&role.id!=='super_admin'?`<button data-save-role="${escapeHtml(role.id)}">保存权限</button>`:''}</div>`).join('')}`
  if($('createRole')) $('createRole').onsubmit=async event=>{event.preventDefault();try{await api('/roles',{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData(event.target)))});message('角色已创建');await roles()}catch(error){message(error.message)}}
  document.querySelectorAll('[data-save-role]').forEach(button=>button.onclick=async()=>{const id=button.dataset.saveRole;const selected=[...document.querySelectorAll('input[data-for="'+id+'"]:checked')].map(item=>item.value);try{await api('/roles/'+encodeURIComponent(id)+'/permissions',{method:'PUT',body:JSON.stringify({permissions:selected})});message('权限已保存');await boot()}catch(error){message(error.message)}})
}
async function audit() {
  const rows=await api('/audit')
  $('content').innerHTML=`<div class="panel"><h2>操作审计</h2><table><tr><th>时间</th><th>操作人</th><th>动作</th><th>目标</th><th>详情</th></tr>${rows.map(row=>`<tr><td>${escapeHtml(row.createdAt)}</td><td>${escapeHtml(row.username)}</td><td>${escapeHtml(row.action)}</td><td>${escapeHtml(row.targetId)}</td><td>${escapeHtml(row.detail)}</td></tr>`).join('')}</table></div>`
}
boot()
