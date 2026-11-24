# 本地开发环境

## 启动

需要 Docker/Colima、JDK 11+ 和微信开发者工具。仓库根目录的 `./mvnw` 会在首次运行时下载固定版本 Maven。

```sh
docker compose up -d mysql
docker compose exec mysql mysql -u cos_dev -plocal-dev-only cos_marketplace -e 'SHOW TABLES;'
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

MySQL 在 `127.0.0.1:3307`，初始化为下载的 MySQL v4 11 表方案。首次建库后如需重建容器数据，请先确认不需要保留本地数据，再运行 `docker compose down -v && docker compose up -d mysql`。本地数据库密码在 `compose.yaml` 中配置。

当前 Java API 的 `local` 配置连接 `server/data/local-dev.mv.db`（Flyway V1–V6），默认 `http://localhost:18080`，不改动原有 `weituolaoshi.mv.db`。MySQL 是下一阶段的目标库；两套结构和迁移边界见 `server/docs/v4-schema-integration.md`。不要将 H2 的 V1–V6 迁移直接跑到 MySQL。微信开发者工具中导入 `wechat-miniprogram/`，本地调试在小程序设置中关闭合法域名校验。开发者工具会自动连接 `127.0.0.1:18080`，并使用只在 Spring `local` 配置启用的开发账号登录；真机和正式环境仍使用微信登录。实名核验需要供应商配置，详见 `server/README.md`。

## 当前构建顺序

1. MySQL 目标模型建表并验证约束。
2. 后端实现基于角色、作品/委托帖和档期的真实接口，逐步替换小程序页面的本地演示数据。
3. 接入支付回调、退款、审核、媒体与消息；上线前关闭演示身份头，并完成 MySQL 数据迁移和并发测试。
