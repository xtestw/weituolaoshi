import React, { useEffect, useState } from 'react'
import { Alert, Button, Card, Descriptions, Empty, Image, Modal, Space, Table, Typography } from 'antd'

function Screenshot({ id }) {
  const [url, setUrl] = useState('')
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    let objectUrl
    setError('')
    setUrl('')
    fetch('/admin/api/feedback/images/' + encodeURIComponent(id), {
      headers: { Authorization: `Bearer ${sessionStorage.getItem('admin_token')}` }, signal: controller.signal
    }).then(async response => {
      if (!response.ok) throw new Error('截图加载失败')
      const blob = await response.blob()
      if (controller.signal.aborted) return
      objectUrl = URL.createObjectURL(blob)
      setUrl(objectUrl)
    }).catch(error => { if (!controller.signal.aborted) setError(error.message) })
    return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [id, retry])
  return error ? <Button onClick={() => setRetry(value => value + 1)}>截图加载失败，点击重试</Button> : url ? <Image src={url} width={150} height={150} style={{ objectFit: 'cover' }} alt="用户反馈截图" /> : <Typography.Text type="secondary">截图加载中…</Typography.Text>
}

export default function Feedback({ api }) {
  const [rows, setRows] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [refresh, setRefresh] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState(null)
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    api('/feedback?page=' + page).then(result => {
      if (active) { setRows(result.items); setTotal(result.total) }
    }).catch(error => { if (active) setError(error.message) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, refresh, api])
  const time = value => String(value).replace('T', ' ').slice(0, 19)
  return <>
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
    <Card extra={<Button onClick={() => setRefresh(value => value + 1)} loading={loading}>刷新</Button>}>
      <Table rowKey="id" loading={loading} dataSource={rows} scroll={{ x: 850 }} locale={{ emptyText: <Empty description="暂无用户反馈" /> }} pagination={{ current: page, pageSize: 20, total, showSizeChanger: false, onChange: setPage }} columns={[
        { title: '提交时间', dataIndex: 'createdAt', render: time, width: 180 },
        { title: '用户', render: (_, row) => <div>{row.nickname || '未设置昵称'}<br /><Typography.Text type="secondary">{row.userId}</Typography.Text></div> },
        { title: '反馈内容', dataIndex: 'body', ellipsis: true },
        { title: '联系方式', dataIndex: 'contact', render: value => value || '未填写' },
        { title: '截图', render: (_, row) => `${row.images.length}张`, width: 70 },
        { title: '操作', render: (_, row) => <Button onClick={() => setSelected(row)}>查看详情</Button>, width: 110 }
      ]} />
    </Card>
    <Modal title="用户反馈详情" open={!!selected} onCancel={() => setSelected(null)} footer={<Button onClick={() => setSelected(null)}>关闭</Button>} width={760} destroyOnHidden>
      {selected && <>
        <Descriptions column={1} items={[
          { key: 'id', label: '反馈编号', children: selected.id },
          { key: 'user', label: '提交用户', children: `${selected.nickname || '未设置昵称'}（${selected.userId}）` },
          { key: 'time', label: '提交时间', children: time(selected.createdAt) },
          { key: 'contact', label: '联系方式', children: selected.contact || '未填写' }
        ]} />
        <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', marginTop: 20 }}>{selected.body}</Typography.Paragraph>
        <Image.PreviewGroup><Space wrap>{selected.images.map(id => <Screenshot key={id} id={id} />)}</Space></Image.PreviewGroup>
      </>}
    </Modal>
  </>
}
