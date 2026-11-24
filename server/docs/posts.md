# 帖子内容模型

平台目前有两条独立内容链路：

| 类型 | 数据表 | 作者 | 分发与详情 | 后续方向 |
|---|---|---|---|---|
| 社区帖 | `customer_post` + `customer_post_like` + `customer_post_comment` | 普通用户账号 | `/v1/posts`、`/v1/posts/{id}` | 社区互动与内容发现 |
| 角色委托服务帖 | `service_post` + `service_media` + `service_boundary` + `service_question` | 已认证 Coser | `/v1/services`、`/v1/services/{id}` | 角色服务、档期与交易 |

社区帖支持创建、草稿、编辑、删除、公开列表、同城过滤、关键词搜索、详情、点赞与一级评论。Coser 服务帖支持创建草稿、编辑、图片上传/删除、封面与顺序同步、发布、删除、公开列表、关键词搜索和详情；`GET /v1/services/mine` 返回 `locked`（存在进行中委托时不可改删）。服务帖至少有一张图片才可发布；仅公开且已发布的内容进入分发。两类帖子在首页与搜索页分区显示，详情页分别打开。

## 社区互动 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/v1/posts/{id}/likes` | 点赞（幂等） |
| DELETE | `/v1/posts/{id}/likes` | 取消点赞 |
| GET | `/v1/posts/{id}/comments` | 评论列表 |
| POST | `/v1/posts/{id}/comments` | 发表评论 |
| DELETE | `/v1/posts/{id}/comments/{commentId}` | 作者软删评论 |

列表/详情附带 `likeCount`、`commentCount`、`liked`（登录时）。

## 服务帖媒体 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/v1/services/{id}/images` | 上传，返回 `{id,url}` |
| DELETE | `/v1/services/{id}/images/{mediaId}` | 软删并回落封面 |
| PUT | `/v1/services/{id}/images/layout` | `{mediaIds,coverMediaId}` 排序与封面 |

小程序以 uni-app 版 `uni-app/` 为准（`wechat-miniprogram/` 仅作历史对照）。图片当前保存在服务端本地目录 `posts.media-directory`。正式环境需要持久化共享存储、内容审核和媒体处理；当前后端 `audit_status=approved` 的默认值仅适合本地联调，不能据此当作生产审核完成。

## 后续能力清单

| 优先级 | 能力 | 说明 |
|---|---|---|
| P1 上线前 | 对象存储 + presign | 替换本地 `posts.media-directory`；`POST /v1/uploads/presign` 仍为 stub |
| P1 上线前 | 真实内容审核 | 停上传即 `audit_status=approved` |
| P1 交易闭环 | 微信支付 | 替换 `/orders/{id}/payment` 501 |
| P2 | 社区配图 / 草稿预览 / 显式分享按钮 | |
| P2 | 媒体缩略图与处理管线 | schema 已有 thumbnail/processing 字段 |
| P2 | Feed 关注/专题真数据 | 首页 tab 目前为空或等同推荐 |
| P2 | 嵌套评论回复 | 当前仅一级评论 |
