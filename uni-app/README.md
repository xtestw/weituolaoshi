# 次元委托 uni-app 小程序

基于 Vue 3 和 uni-app 的微信小程序版本。原生工程保留在 `../wechat-miniprogram`，本工程包含对应的 18 个页面、2 个组件、业务工具和 API 接入。

## 开发

```bash
cd uni-app
npm install
npm run dev:mp-weixin
```

在微信开发者工具中导入 `uni-app/dist/dev/mp-weixin`。发布构建使用 `npm run build:mp-weixin`，导入 `uni-app/dist/build/mp-weixin`。AppID 配置在 `src/manifest.json`；开发构建也会同步此 AppID，避免开发者工具使用 `touristappid` 导致微信登录返回 40029。

仅开发构建、微信开发者工具、开发版（develop）三个条件同时满足时连接 `http://127.0.0.1:18080`，此时才允许 `src/config.js` 的 `useDemoAccount` 开关生效。生产构建、体验版、正式版及真机均连接 `https://wtls.xtestw.com`，关闭本地开发模式和演示登录。环境识别失败时也使用线上服务。原项目 README 中的后端接入和上线前要求仍适用。

迁移源自原生工程，`migrate.py` 是一次性迁移脚本。后续功能开发请直接修改 `src/` 下的 Vue 文件；运行脚本会重新生成页面和组件，并覆盖 `src/utils`、`src/services`。

页面底栏使用 uni-app 原生 tabBar，视觉样式与原生工程的自定义 tabBar 略有差异。

## 线上域名

微信公众平台 → 开发管理 → 开发设置 → 服务器域名，配置：

- request、uploadFile、downloadFile：`https://wtls.xtestw.com`
- socket：`wss://wtls.xtestw.com`

普通请求、头像和帖子图片上传、媒体地址统一使用线上 API；聊天 WebSocket 使用 `wss://wtls.xtestw.com/v1/messages/socket`。域名白名单需在微信公众平台单独保存，代码配置不会自动修改平台设置。
