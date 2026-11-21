import React, { useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { ConfigProvider, Layout, Menu, Card, Form, Input, Button, Table, Tag, Space, Typography, Alert, Modal, Select, Switch, Checkbox, message, Grid, Drawer, Empty } from 'antd'
import { SafetyCertificateOutlined, TeamOutlined, UserOutlined, KeyOutlined, AuditOutlined, LogoutOutlined, MenuOutlined, CheckCircleOutlined } from '@ant-design/icons'
import 'antd/dist/reset.css'
import './style.css'

const { Sider, Header, Content } = Layout
const { Text, Title } = Typography
const api = async (path, options = {}) => {
  const token = sessionStorage.getItem('admin_token')
  const response = await fetch('/admin/api' + path, { ...options, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...options.headers } })
  const data = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(data.message || '请求失败，请稍后重试')
  return data
}
const send = (path, method, body) => api(path, { method, body: JSON.stringify(body) })
const fmt = value => value ? String(value).replace('T', ' ').slice(0, 19) : '—'
const statusColor = status => ({ verified: 'success', pending: 'processing', rejected: 'error', active: 'success', frozen: 'warning', closed: 'default' })[status] || 'default'
const statusLabel = status => ({ verified: '已通过', pending: '待审核', rejected: '已驳回', active: '正常', frozen: '已冻结', closed: '已注销' })[status] || status || '—'
const navItems = [
  { key: 'identities', icon: <SafetyCertificateOutlined />, label: '实名认证', permission: 'identity:read' },
  { key: 'users', icon: <TeamOutlined />, label: '用户管理', permission: 'users:read' },
  { key: 'admins', icon: <UserOutlined />, label: '管理员', permission: 'admins:read' },
  { key: 'roles', icon: <KeyOutlined />, label: '角色权限', permission: 'admins:read' },
  { key: 'audit', icon: <AuditOutlined />, label: '审计日志', permission: 'audit:read' }
]
function App() {
  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [tab, setTab] = useState('identities')
  const [drawer, setDrawer] = useState(false)
  const [notice, setNotice] = useState('')
  const screens = Grid.useBreakpoint()
  const can = permission => profile?.permissions?.includes(permission)
  const available = navItems.filter(item => can(item.permission))
  useEffect(() => { if (sessionStorage.getItem('admin_token')) api('/me').then(setProfile).catch(() => sessionStorage.removeItem('admin_token')).finally(() => setLoading(false)); else setLoading(false) }, [])
  useEffect(() => { if (profile && !available.some(item => item.key === tab)) setTab(available[0]?.key || '') }, [profile, tab])
  const login = async values => { try { const result = await send('/login', 'POST', values); sessionStorage.setItem('admin_token', result.token); setProfile(result.profile); message.success('登录成功') } catch (error) { message.error(error.message) } }
  const logout = async () => { try { await api('/logout', { method: 'POST' }) } catch {} sessionStorage.removeItem('admin_token'); setProfile(null); setTab('identities') }
  const choose = key => { setTab(key); setDrawer(false); setNotice('') }
  const menu = <Menu theme="dark" mode="inline" selectedKeys={[tab]} items={available.map(({ key, icon, label }) => ({ key, icon, label }))} onClick={({ key }) => choose(key)} />
  if (loading) return null
  if (!profile) return <div className="login-page"><Card className="login-card"><Space direction="vertical" size={4} style={{ marginBottom: 28 }}><SafetyCertificateOutlined style={{ fontSize: 32, color: '#1677ff' }} /><Title level={3} style={{ margin: '12px 0 0' }}>次元委托管理后台</Title><Text type="secondary">请使用管理员账号登录</Text></Space><Form layout="vertical" onFinish={login} size="large"><Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}><Input autoComplete="username" /></Form.Item><Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}><Input.Password autoComplete="current-password" /></Form.Item><Button type="primary" htmlType="submit" block>登录</Button></Form></Card></div>
  const current = available.find(item => item.key === tab)
  return <Layout className="app-shell">
    {screens.md ? <Sider width={230} className="app-sider" theme="dark"><div className="sider-brand"><div className="brand">次元委托</div><div className="brand-sub">管理后台</div></div>{menu}</Sider> : <Drawer title="次元委托 · 管理后台" placement="left" width={230} open={drawer} onClose={() => setDrawer(false)} styles={{ body: { padding: 0, background: '#001529' } }}>{menu}</Drawer>}
    <Layout><Header className="app-header"><Space>{!screens.md && <Button className="mobile-trigger" type="text" icon={<MenuOutlined />} onClick={() => setDrawer(true)} />}<Text strong>{current?.label || '工作台'}</Text></Space><div className="header-user"><Text className="username">{profile.username}</Text><Tag color="blue">{profile.roles?.join('、')}</Tag><Button type="text" icon={<LogoutOutlined />} onClick={logout}>退出</Button></div></Header>
      <Content className="app-content"><h1 className="page-title">{current?.label || '工作台'}</h1><p className="page-caption">{({ identities: '查看自动核验结果与脱敏证件信息', users: '查看平台用户信息', admins: '管理后台账号及角色', roles: '配置各角色可使用的功能', audit: '查看管理员操作记录' })[tab]}</p>{notice && <Alert type="error" showIcon closable message={notice} onClose={() => setNotice('')} style={{ marginBottom: 16 }} />}{!tab ? <Empty description="当前账号没有可用菜单" /> : <Page key={tab} tab={tab} profile={profile} setProfile={setProfile} setNotice={setNotice} />}</Content>
    </Layout>
  </Layout>
}
function Page({ tab, profile, setProfile, setNotice }) {
  const [rows, setRows] = useState([])
  const [roles, setRoles] = useState([])
  const [permissions, setPermissions] = useState([])
  const [loading, setLoading] = useState(true)
  const [modal, setModal] = useState(null)
  const [form] = Form.useForm()
  const canWrite = tab !== 'identities' && profile.permissions.includes('admins:write')
  const load = async () => { setLoading(true); try { if (tab === 'admins') { const [a, b] = await Promise.all([api('/admins'), api('/roles')]); setRows(a); setRoles(b) } else if (tab === 'roles') { const [a, b] = await Promise.all([api('/roles'), api('/permissions')]); setRows(a); setPermissions(b) } else setRows(await api('/' + tab)) } catch (error) { setNotice(error.message) } finally { setLoading(false) } }
  useEffect(() => { load() }, [tab])
  const success = async text => { message.success(text); setModal(null); form.resetFields(); await load() }
  const fail = error => message.error(error.message)
  const submitModal = async values => {
    try {
      if (modal.kind === 'admin') await send('/admins', 'POST', values)
      if (modal.kind === 'role') await send('/roles', 'POST', values)
      if (modal.kind === 'reset') await send('/admins/' + encodeURIComponent(modal.row.id), 'PATCH', { password: values.password })
      await success('操作已保存')
    } catch (error) { fail(error) }
  }
  const changeAdmin = async (row, values) => { try { await send('/admins/' + encodeURIComponent(row.id), 'PATCH', values); await success('管理员已更新') } catch (error) { fail(error) } }
  const savePermissions = async (role, selected) => { try { await send('/roles/' + encodeURIComponent(role.id) + '/permissions', 'PUT', { permissions: selected }); message.success('权限已保存'); const p = await api('/me'); setProfile(p); await load() } catch (error) { fail(error) } }
  const columns = {
    identities: [
      { title: '用户', dataIndex: 'nickname', render: (value, row) => <div className="user-cell">{value || '未设置昵称'}<small title={row.userId}>{row.userId}</small></div> },
      { title: '类型', dataIndex: 'mode', render: value => value === 'coser' ? 'Coser' : '用户' },
      { title: '证件（脱敏）', dataIndex: 'maskedId', render: value => value || '—' },
      { title: '状态', dataIndex: 'status', render: value => <Tag color={statusColor(value)}>{statusLabel(value)}</Tag> },
      { title: '原因', dataIndex: 'reason', render: value => value || '—' },
      { title: '提交时间', dataIndex: 'createdAt', render: fmt },
    ],
    users: [{ title: '用户 ID', dataIndex: 'id' }, { title: '昵称', dataIndex: 'nickname', render: v => v || '—' }, { title: '角色', dataIndex: 'role' }, { title: '注册时间', dataIndex: 'createdAt', render: fmt }],
    admins: [{ title: '用户名', dataIndex: 'username' }, { title: '状态', dataIndex: 'enabled', render: v => <Tag color={v ? 'success' : 'default'}>{v ? '启用' : '禁用'}</Tag> }, { title: '操作', key: 'actions', render: (_, row) => canWrite && row.id !== profile.id ? <Space wrap><Select size="small" placeholder="修改角色" style={{ width: 125 }} options={roles.map(r => ({ value: r.id, label: r.name }))} onChange={role => changeAdmin(row, { role })} /><Switch size="small" checked={row.enabled} onChange={enabled => changeAdmin(row, { enabled })} /><Button size="small" onClick={() => { form.resetFields(); setModal({ kind: 'reset', row }) }}>重置密码</Button></Space> : '—' }],
    audit: [{ title: '时间', dataIndex: 'createdAt', render: fmt }, { title: '操作人', dataIndex: 'username' }, { title: '动作', dataIndex: 'action' }, { title: '目标', dataIndex: 'targetId' }, { title: '详情', dataIndex: 'detail', render: v => v || '—' }]
  }
  return <>
    {tab === 'roles' ? <>{canWrite && <Button type="primary" style={{ marginBottom: 16 }} onClick={() => { form.resetFields(); setModal({ kind: 'role' }) }}>新建角色</Button>}{rows.map(role => <RoleCard key={role.id} role={role} permissions={permissions} canWrite={canWrite} onSave={savePermissions} />)}</> : <Card className="content-card" extra={tab === 'admins' && canWrite ? <Button type="primary" onClick={() => { form.resetFields(); setModal({ kind: 'admin' }) }}>新建管理员</Button> : null}><div className="table-scroll"><Table rowKey={row => row.id || `${row.createdAt}-${row.action}-${row.targetId}`} loading={loading} dataSource={rows} columns={columns[tab]} pagination={{ pageSize: 10, showSizeChanger: false }} /></div></Card>}
    <Modal title={{ admin: '新建管理员', role: '新建角色', reset: '重置管理员密码' }[modal?.kind]} open={!!modal} onCancel={() => setModal(null)} onOk={() => form.submit()} okText="确认" cancelText="取消" destroyOnHidden><Form form={form} layout="vertical" onFinish={submitModal} style={{ paddingTop: 12 }}>
      {modal?.kind === 'admin' && <><Form.Item name="username" label="用户名" rules={[{ required: true }, { pattern: /^[A-Za-z0-9_]{3,40}$/, message: '请输入 3–40 位字母、数字或下划线' }]}><Input /></Form.Item><Form.Item name="password" label="密码" rules={[{ required: true }, { min: 12, message: '密码至少 12 位' }]}><Input.Password autoComplete="new-password" /></Form.Item><Form.Item name="role" label="角色" rules={[{ required: true }]}><Select options={roles.map(r => ({ value: r.id, label: r.name }))} /></Form.Item></>}
      {modal?.kind === 'role' && <><Form.Item name="id" label="角色标识" rules={[{ required: true }, { pattern: /^[a-z_]{3,40}$/, message: '请输入 3–40 位小写字母或下划线' }]}><Input placeholder="例如 support" /></Form.Item><Form.Item name="name" label="显示名称" rules={[{ required: true }, { max: 80 }]}><Input /></Form.Item></>}
      {modal?.kind === 'reset' && <Form.Item name="password" label="新密码" rules={[{ required: true }, { min: 12, message: '密码至少 12 位' }]}><Input.Password autoComplete="new-password" /></Form.Item>}
    </Form></Modal>
  </>
}
function RoleCard({ role, permissions, canWrite, onSave }) {
  const [selected, setSelected] = useState(role.permissions || [])
  useEffect(() => setSelected(role.permissions || []), [role.permissions])
  const editable = canWrite && role.id !== 'super_admin'
  return <Card className="content-card role-card" title={<Space><KeyOutlined /><span>{role.name}</span><Text type="secondary" style={{ fontSize: 12 }}>{role.id}</Text></Space>} extra={editable && <Button type="primary" onClick={() => onSave(role, selected)}>保存权限</Button>}><Checkbox.Group value={selected} onChange={setSelected} disabled={!editable}><div className="permission-grid">{permissions.map(p => <Checkbox key={p.code} value={p.code}>{p.description}</Checkbox>)}</div></Checkbox.Group>{role.id === 'super_admin' && <Text type="secondary"><CheckCircleOutlined /> 超级管理员权限不可修改</Text>}</Card>
}
createRoot(document.getElementById('root')).render(<ConfigProvider theme={{ token: { colorPrimary: '#1677ff', borderRadius: 8 } }}><App /></ConfigProvider>)
