import React, { useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { ConfigProvider, Layout, Menu, Card, Form, Input, Button, Table, Tag, Space, Typography, Alert, Modal, Select, Switch, Checkbox, message, Grid, Drawer, Empty } from 'antd'
import { SafetyCertificateOutlined, TeamOutlined, UserOutlined, KeyOutlined, AuditOutlined, LogoutOutlined, MenuOutlined, CheckCircleOutlined, MailOutlined } from '@ant-design/icons'
import 'antd/dist/reset.css'
import './style.css'

const { Sider, Header, Content } = Layout
const { Text, Title } = Typography
const { TextArea } = Input
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
  { key: 'identity-whitelist', icon: <SafetyCertificateOutlined />, label: '身份证白名单', permission: 'identity:whitelist' },
  { key: 'identities', icon: <SafetyCertificateOutlined />, label: '实名认证', permission: 'identity:read' },
  { key: 'users', icon: <TeamOutlined />, label: '用户管理', permission: 'users:read' },
  { key: 'coser-posts', icon: <AuditOutlined />, label: 'Coser帖子', permission: 'services:read' },
  { key: 'official-messages', icon: <MailOutlined />, label: '官号私信', permission: 'messages:send' },
  { key: 'admins', icon: <UserOutlined />, label: '管理员', permission: 'admins:read' },
  { key: 'roles', icon: <KeyOutlined />, label: '角色权限', permission: 'admins:read' },
  { key: 'audit', icon: <AuditOutlined />, label: '审计日志', permission: 'audit:read' },
  { key: 'messages', icon: <AuditOutlined />, label: '消息审核', permission: 'messages:read' },
  { key: 'message-reports', icon: <AuditOutlined />, label: '消息举报', permission: 'messages:read' }
]
const captions = {
  identities: '查看自动核验结果与脱敏证件信息',
  users: '编辑用户资料、账号状态和 Coser 认证；实名核验结果由核验流程产生',
  'coser-posts': '查看 Coser 服务帖子，人工下线或恢复展示',
  'official-messages': '以「委托助手」官号向指定用户发送私信',
  admins: '管理后台账号及角色',
  roles: '配置各角色可使用的功能',
  audit: '查看管理员操作记录',
  messages: '审核用户消息',
  'message-reports': '处理消息举报'
}
function App() {
  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [tab, setTab] = useState('identities')
  const [drawer, setDrawer] = useState(false)
  const [notice, setNotice] = useState('')
  const [officialDraft, setOfficialDraft] = useState('')
  const screens = Grid.useBreakpoint()
  const can = permission => profile?.permissions?.includes(permission)
  const available = navItems.filter(item => can(item.permission))
  useEffect(() => { if (sessionStorage.getItem('admin_token')) api('/me').then(setProfile).catch(() => sessionStorage.removeItem('admin_token')).finally(() => setLoading(false)); else setLoading(false) }, [])
  useEffect(() => { if (profile && !available.some(item => item.key === tab)) setTab(available[0]?.key || '') }, [profile, tab])
  const login = async values => { try { const result = await send('/login', 'POST', values); sessionStorage.setItem('admin_token', result.token); setProfile(result.profile); message.success('登录成功') } catch (error) { message.error(error.message) } }
  const logout = async () => { try { await api('/logout', { method: 'POST' }) } catch {} sessionStorage.removeItem('admin_token'); setProfile(null); setTab('identities'); setOfficialDraft('') }
  const choose = key => { setTab(key); setDrawer(false); setNotice(''); if (key !== 'official-messages') setOfficialDraft('') }
  const openOfficial = userId => { setOfficialDraft(userId); setTab('official-messages'); setDrawer(false); setNotice('') }
  const menu = <Menu theme="dark" mode="inline" selectedKeys={[tab]} items={available.map(({ key, icon, label }) => ({ key, icon, label }))} onClick={({ key }) => choose(key)} />
  if (loading) return null
  if (!profile) return <div className="login-page"><Card className="login-card"><Space direction="vertical" size={4} style={{ marginBottom: 28 }}><SafetyCertificateOutlined style={{ fontSize: 32, color: '#1677ff' }} /><Title level={3} style={{ margin: '12px 0 0' }}>次元委托管理后台</Title><Text type="secondary">请使用管理员账号登录</Text></Space><Form layout="vertical" onFinish={login} size="large"><Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}><Input autoComplete="username" /></Form.Item><Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}><Input.Password autoComplete="current-password" /></Form.Item><Button type="primary" htmlType="submit" block>登录</Button></Form></Card></div>
  const current = available.find(item => item.key === tab)
  return <Layout className="app-shell">
    {screens.md ? <Sider width={230} className="app-sider" theme="dark"><div className="sider-brand"><div className="brand">次元委托</div><div className="brand-sub">管理后台</div></div>{menu}</Sider> : <Drawer title="次元委托 · 管理后台" placement="left" width={230} open={drawer} onClose={() => setDrawer(false)} styles={{ body: { padding: 0, background: '#001529' } }}>{menu}</Drawer>}
    <Layout><Header className="app-header"><Space>{!screens.md && <Button className="mobile-trigger" type="text" icon={<MenuOutlined />} onClick={() => setDrawer(true)} />}<Text strong>{current?.label || '工作台'}</Text></Space><div className="header-user"><Text className="username">{profile.username}</Text><Tag color="blue">{profile.roles?.join('、')}</Tag><Button type="text" icon={<LogoutOutlined />} onClick={logout}>退出</Button></div></Header>
      <Content className="app-content"><h1 className="page-title">{current?.label || '工作台'}</h1><p className="page-caption">{captions[tab]}</p>{notice && <Alert type="error" showIcon closable message={notice} onClose={() => setNotice('')} style={{ marginBottom: 16 }} />}{!tab ? <Empty description="当前账号没有可用菜单" /> : <Page key={tab} tab={tab} profile={profile} setProfile={setProfile} setNotice={setNotice} officialDraft={officialDraft} onOpenOfficial={openOfficial} canSendOfficial={can('messages:send')} />}</Content>
    </Layout>
  </Layout>
}
function Page({ tab, profile, setProfile, setNotice, officialDraft, onOpenOfficial, canSendOfficial }) {
  const [rows, setRows] = useState([])
  const [roles, setRoles] = useState([])
  const [permissions, setPermissions] = useState([])
  const [loading, setLoading] = useState(true)
  const [modal, setModal] = useState(null)
  const [sending, setSending] = useState(false)
  const [form] = Form.useForm()
  const [officialForm] = Form.useForm()
  const canWrite = tab !== 'identities' && profile.permissions.includes(tab === 'messages' || tab === 'message-reports' ? 'messages:moderate' : 'admins:write')
  const load = async () => {
    setLoading(true)
    try {
      if (tab === 'admins') { const [a, b] = await Promise.all([api('/admins'), api('/roles')]); setRows(a); setRoles(b) }
      else if (tab === 'roles') { const [a, b] = await Promise.all([api('/roles'), api('/permissions')]); setRows(a); setPermissions(b) }
      else setRows(await api('/' + tab))
    } catch (error) { setNotice(error.message) } finally { setLoading(false) }
  }
  useEffect(() => { load() }, [tab])
  useEffect(() => {
    if (tab === 'official-messages') officialForm.setFieldsValue({ userId: officialDraft || undefined, body: undefined })
  }, [tab, officialDraft])
  const success = async text => { message.success(text); setModal(null); form.resetFields(); await load() }
  const fail = error => message.error(error.message)
  const submitModal = async values => {
    try {
      if (modal.kind === 'admin') await send('/admins', 'POST', values)
      if (modal.kind === 'role') await send('/roles', 'POST', values)
      if (modal.kind === 'reset') await send('/admins/' + encodeURIComponent(modal.row.id), 'PATCH', { password: values.password })
      if (modal.kind === 'user') await send('/users/' + encodeURIComponent(modal.row.id), 'PATCH', values)
      if (modal.kind === 'offline-post') await send('/coser-posts/' + encodeURIComponent(modal.row.id) + '/moderation', 'PATCH', { action: 'offline', reason: values.reason })
      await success('操作已保存')
    } catch (error) { fail(error) }
  }
  const changeAdmin = async (row, values) => { try { await send('/admins/' + encodeURIComponent(row.id), 'PATCH', values); await success('管理员已更新') } catch (error) { fail(error) } }
  const certifyCoser = (row, status) => Modal.confirm({ title: status === 'verified' ? '直接认证 Coser？' : '撤销 Coser 认证？', content: status === 'verified' ? `将 ${row.nickname || row.id} 设为已认证 Coser。该用户必须已完成符合平台规则的成年实名认证。` : `撤销 ${row.nickname || row.id} 的 Coser 认证后，其服务将停止展示。`, onOk: async () => { try { await send('/users/' + encodeURIComponent(row.id) + '/coser-verification', 'PATCH', { status }); message.success('Coser 认证状态已更新'); await load() } catch (error) { fail(error); throw error } } })
  const restorePost = row => Modal.confirm({ title: '恢复帖子展示？', content: `将重新展示「${row.title}」。`, onOk: async () => { try { await send('/coser-posts/' + encodeURIComponent(row.id) + '/moderation', 'PATCH', { action: 'restore' }); message.success('帖子已恢复展示'); await load() } catch (error) { fail(error); throw error } } })
  const editUser = row => { form.resetFields(); form.setFieldsValue({ nickname: row.nickname || '', phone: row.phone || '', bio: row.bio || '', accountStatus: row.accountStatus || 'active', ...(row.coserStatus ? { coserDisplayName: row.coserDisplayName || '', coserCity: row.coserCity || '' } : {}) }); setModal({ kind: 'user', row }) }
  const moderate = async (row, decision) => { try { await send('/messages/' + encodeURIComponent(row.messageId || row.id), 'PATCH', { decision }); await success('消息已处置') } catch (error) { fail(error) } }
  const restrict = async row => { try { await send('/conversations/' + encodeURIComponent(row.conversationId) + '/restriction', 'PATCH', { restricted: true, reason: '管理员限制' }); await success('会话已限制') } catch (error) { fail(error) } }
  const viewImage = async row => { try { const response = await fetch('/admin/api/messages/' + encodeURIComponent(row.id) + '/image', { headers: { Authorization: `Bearer ${sessionStorage.getItem('admin_token')}` } }); if (!response.ok) throw new Error('图片加载失败'); const url = URL.createObjectURL(await response.blob()); window.open(url, '_blank'); setTimeout(() => URL.revokeObjectURL(url), 60000) } catch (error) { fail(error) } }
  const savePermissions = async (role, selected) => { try { await send('/roles/' + encodeURIComponent(role.id) + '/permissions', 'PUT', { permissions: selected }); message.success('权限已保存'); const p = await api('/me'); setProfile(p); await load() } catch (error) { fail(error) } }
  const sendOfficial = async values => {
    setSending(true)
    try {
      await send('/official-messages', 'POST', values)
      message.success('私信已发送')
      officialForm.setFieldsValue({ body: undefined })
      await load()
    } catch (error) { fail(error) } finally { setSending(false) }
  }
  const columns = {
    identities: [
      { title: '用户', dataIndex: 'nickname', render: (value, row) => <div className="user-cell">{value || '未设置昵称'}<small title={row.userId}>{row.userId}</small></div> },
      { title: '类型', dataIndex: 'mode', render: value => value === 'coser' ? 'Coser' : '用户' },
      { title: '证件（脱敏）', dataIndex: 'maskedId', render: value => value || '—' },
      { title: '状态', dataIndex: 'status', render: value => <Tag color={statusColor(value)}>{statusLabel(value)}</Tag> },
      { title: '原因', dataIndex: 'reason', render: value => value || '—' },
      { title: '提交时间', dataIndex: 'createdAt', render: fmt },
    ],
    users: [
      { title: '用户 ID', dataIndex: 'id' },
      { title: '昵称', dataIndex: 'nickname', render: v => v || '—' },
      { title: '角色', dataIndex: 'role' },
      { title: '账号状态', dataIndex: 'accountStatus', render: value => <Tag color={statusColor(value)}>{statusLabel(value)}</Tag> },
      { title: '实名状态', render: (_, row) => <Tag color={statusColor(row.identityStatus)}>{statusLabel(row.identityStatus)}</Tag> },
      { title: 'Coser认证', render: (_, row) => <Tag color={statusColor(row.coserStatus)}>{statusLabel(row.coserStatus)}</Tag> },
      { title: '注册时间', dataIndex: 'createdAt', render: fmt },
      { title: '操作', key: 'actions', render: (_, row) => <Space wrap>{profile.permissions.includes('users:write') && row.role !== 'official' && <Button size="small" onClick={() => editUser(row)}>编辑资料</Button>}{canSendOfficial && row.role !== 'official' && <Button size="small" onClick={() => onOpenOfficial(row.id)}>发私信</Button>}{profile.permissions.includes('identity:certify-coser') && row.role !== 'official' && (row.coserStatus === 'verified' ? <Button size="small" danger onClick={() => certifyCoser(row, 'unverified')}>撤销认证</Button> : <Button size="small" type="primary" disabled={row.identityStatus !== 'verified' || row.adult !== true || row.genderEligible !== true} onClick={() => certifyCoser(row, 'verified')}>直接认证 Coser</Button>)}</Space> }
    ],
    'coser-posts': [
      { title: '标题', dataIndex: 'title', render: (value, row) => <div className="user-cell">{value}<small title={row.id}>{row.id}</small></div> },
      { title: 'Coser', render: (_, row) => <div className="user-cell">{row.coserName || row.coserNickname || '—'}<small title={row.coserId}>{row.coserId}</small></div> },
      { title: '城市', dataIndex: 'city' },
      { title: '价格', dataIndex: 'pricePerHour', render: value => `¥${(Number(value) / 100).toFixed(2)}/小时` },
      { title: '状态', render: (_, row) => <Space><Tag color={row.status === 'published' ? 'success' : 'default'}>{row.status === 'published' ? '已发布' : '草稿/未发布'}</Tag>{row.manuallyOffline ? <Tag color="error">人工下线</Tag> : row.status === 'published' && row.visibility === 'public' && row.auditStatus === 'approved' ? <Tag color="success">展示中</Tag> : <Tag>未展示</Tag>}</Space> },
      { title: '发布时间', dataIndex: 'createdAt', render: fmt },
      { title: '操作', render: (_, row) => <Space wrap><Button size="small" onClick={() => setModal({ kind: 'post-detail', row })}>详情</Button>{profile.permissions.includes('services:moderate') && (row.manuallyOffline ? <Button size="small" onClick={() => restorePost(row)}>恢复展示</Button> : row.status === 'published' && row.visibility === 'public' && row.auditStatus === 'approved' && <Button size="small" danger onClick={() => { form.resetFields(); setModal({ kind: 'offline-post', row }) }}>人工下线</Button>)}</Space> }
    ],
    'official-messages': [
      { title: '时间', dataIndex: 'createdAt', render: fmt },
      { title: '用户', render: (_, row) => <div className="user-cell">{row.nickname || '未设置昵称'}<small title={row.userId}>{row.userId}</small></div> },
      { title: '内容', dataIndex: 'body' },
      { title: '会话', dataIndex: 'conversationId' }
    ],
    admins: [{ title: '用户名', dataIndex: 'username' }, { title: '状态', dataIndex: 'enabled', render: v => <Tag color={v ? 'success' : 'default'}>{v ? '启用' : '禁用'}</Tag> }, { title: '操作', key: 'actions', render: (_, row) => canWrite && row.id !== profile.id ? <Space wrap><Select size="small" placeholder="修改角色" style={{ width: 125 }} options={roles.map(r => ({ value: r.id, label: r.name }))} onChange={role => changeAdmin(row, { role })} /><Switch size="small" checked={row.enabled} onChange={enabled => changeAdmin(row, { enabled })} /><Button size="small" onClick={() => { form.resetFields(); setModal({ kind: 'reset', row }) }}>重置密码</Button></Space> : '—' }],
    audit: [{ title: '时间', dataIndex: 'createdAt', render: fmt }, { title: '操作人', dataIndex: 'username' }, { title: '动作', dataIndex: 'action' }, { title: '目标', dataIndex: 'targetId' }, { title: '详情', dataIndex: 'detail', render: v => v || '—' }],
    messages: [{ title: '时间', dataIndex: 'createdAt', render: fmt }, { title: '发送者', dataIndex: 'senderId' }, { title: '内容', render: (_, row) => row.type === 'image' ? <Button size="small" onClick={() => viewImage(row)}>查看图片</Button> : row.body }, { title: '状态', dataIndex: 'status' }, { title: '会话', dataIndex: 'conversationId' }, { title: '操作', render: (_, row) => canWrite ? <Space><Button size="small" onClick={() => moderate(row, 'approved')}>通过</Button><Button size="small" danger onClick={() => moderate(row, 'rejected')}>驳回</Button><Button size="small" onClick={() => restrict(row)}>限制会话</Button></Space> : '—' }],
    'message-reports': [{ title: '时间', dataIndex: 'createdAt', render: fmt }, { title: '消息', dataIndex: 'messageId' }, { title: '举报人', dataIndex: 'reporterId' }, { title: '原因', dataIndex: 'reason' }, { title: '状态', dataIndex: 'status' }, { title: '操作', render: (_, row) => canWrite ? <Space><Button size="small" onClick={() => moderate(row, 'approved')}>保留消息</Button><Button size="small" danger onClick={() => moderate(row, 'rejected')}>移除消息</Button></Space> : '—' }]
  }
  if (tab === 'identity-whitelist') {
    return <Card title="实名测试白名单">
      <Alert type="warning" showIcon message="仅供测试：名单内证件可跳过人脸核身和性别校验，仍需登录、成年及证件绑定校验。移除后不撤销已有认证。配置文件中的白名单需单独移除。" style={{ marginBottom: 16 }} />
      <Form form={form} layout="inline" onFinish={async values => {
        setSending(true)
        try { await send('/identity-whitelist', 'POST', values); form.resetFields(); message.success('已添加，立即生效'); await load() } catch (error) { fail(error) } finally { setSending(false) }
      }}>
        <Form.Item name="idNumber" rules={[{ required: true, message: '请输入身份证号' }, { pattern: /^[1-9]\d{16}[0-9Xx]$/, message: '请输入18位身份证号' }]}><Input.Password autoComplete="off" placeholder="完整身份证号" maxLength={18} /></Form.Item>
        <Button htmlType="submit" type="primary" loading={sending}>添加白名单</Button>
      </Form>
      <Table style={{ marginTop: 24 }} rowKey="id" loading={loading} dataSource={rows} columns={[
        { title: '身份证（脱敏）', dataIndex: 'maskedId' },
        { title: '添加时间', dataIndex: 'createdAt', render: fmt },
        { title: '操作', render: (_, row) => <Button danger onClick={() => Modal.confirm({ title: '移除该身份证白名单？', content: '移除后不能再通过白名单认证；已有认证状态不变。', onOk: async () => { try { await send('/identity-whitelist/' + encodeURIComponent(row.id), 'DELETE'); await load() } catch(error) { fail(error); throw error } } })}>移除</Button> }
      ]} />
    </Card>
  }
  if (tab === 'official-messages') {
    return <>
      <Card className="content-card" style={{ marginBottom: 16 }} title="发送官号私信">
        <Form form={officialForm} layout="vertical" onFinish={sendOfficial} initialValues={{ userId: officialDraft || undefined }}>
          <Form.Item name="userId" label="目标用户 ID" rules={[{ required: true, message: '请输入用户 ID' }]}><Input placeholder="例如 demo-customer" /></Form.Item>
          <Form.Item name="body" label="消息内容" rules={[{ required: true, message: '请输入内容' }, { max: 2000, message: '最多 2000 字' }]}><TextArea rows={4} showCount maxLength={2000} placeholder="将以「委托助手」名义发送" /></Form.Item>
          <Button type="primary" htmlType="submit" loading={sending}>发送</Button>
        </Form>
      </Card>
      <Card className="content-card" title="最近发送">
        <div className="table-scroll"><Table rowKey="id" loading={loading} dataSource={rows} columns={columns['official-messages']} pagination={{ pageSize: 10, showSizeChanger: false }} /></div>
      </Card>
    </>
  }
  return <>
    {tab === 'roles' ? <>{canWrite && <Button type="primary" style={{ marginBottom: 16 }} onClick={() => { form.resetFields(); setModal({ kind: 'role' }) }}>新建角色</Button>}{rows.map(role => <RoleCard key={role.id} role={role} permissions={permissions} canWrite={canWrite} onSave={savePermissions} />)}</> : <Card className="content-card" extra={tab === 'admins' && canWrite ? <Button type="primary" onClick={() => { form.resetFields(); setModal({ kind: 'admin' }) }}>新建管理员</Button> : null}><div className="table-scroll"><Table rowKey={row => row.id || `${row.createdAt}-${row.action}-${row.targetId}`} loading={loading} dataSource={rows} columns={columns[tab]} pagination={{ pageSize: 10, showSizeChanger: false }} /></div></Card>}
    <Modal title={{ admin: '新建管理员', role: '新建角色', reset: '重置管理员密码', user: '编辑用户资料', 'offline-post': '人工下线 Coser 帖子', 'post-detail': 'Coser 帖子详情' }[modal?.kind]} open={!!modal} onCancel={() => setModal(null)} onOk={modal?.kind === 'post-detail' ? () => setModal(null) : () => form.submit()} okText={modal?.kind === 'post-detail' ? '关闭' : '确认'} cancelText="取消" destroyOnHidden><Form form={form} layout="vertical" onFinish={submitModal} style={{ paddingTop: 12 }}>
      {modal?.kind === 'post-detail' && <Space direction="vertical" style={{ width: '100%' }}><Text strong>{modal.row.title}</Text><Text>发布者：{modal.row.coserName || modal.row.coserNickname}（{modal.row.coserId}）</Text><Text>地点：{modal.row.city}{modal.row.place ? ' · ' + modal.row.place : ''}</Text><Text>价格：¥{(Number(modal.row.pricePerHour) / 100).toFixed(2)}/小时 · 至少{modal.row.minHours}小时</Text><Text>内容：{modal.row.description || '—'}</Text>{modal.row.moderationReason && <Alert type="warning" message={'下线原因：' + modal.row.moderationReason} />}</Space>}
      {modal?.kind === 'offline-post' && <><Alert type="warning" showIcon message="下线后帖子将立即从公开列表和详情中移除；已有订单不受影响。" style={{ marginBottom: 16 }} /><Form.Item name="reason" label="下线原因" rules={[{ required: true, whitespace: true, message: '请填写下线原因' }, { max: 500, message: '最多500字' }]}><TextArea rows={4} maxLength={500} showCount /></Form.Item></>}
      {modal?.kind === 'user' && <><Form.Item name="nickname" label="昵称" rules={[{ required: true, message: '请输入昵称' }, { max: 30 }]}><Input maxLength={30} /></Form.Item><Form.Item name="phone" label="手机号"><Input maxLength={11} /></Form.Item><Form.Item name="bio" label="个人简介"><TextArea maxLength={500} rows={3} /></Form.Item><Form.Item name="accountStatus" label="账号状态"><Select options={[{ value: 'active', label: '正常' }, { value: 'frozen', label: '冻结' }]} /></Form.Item>{modal.row.coserStatus && <><Form.Item name="coserDisplayName" label="Coser 显示名" rules={[{ required: true, message: '请输入 Coser 显示名' }]}><Input maxLength={100} /></Form.Item><Form.Item name="coserCity" label="Coser 服务城市"><Input maxLength={80} /></Form.Item></>}<Alert type="info" showIcon message="实名状态、成年信息和证件性别由核验结果确定，不能在此修改。" /></>}
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
