# HD Password

基于 Kotlin Multiplatform 的本地优先密码管理器，目标平台为 Android、Windows Desktop 和 Web。

## 当前状态

仓库正在按 `openspec/changes/build-kmp-password-manager` 实施。当前已完成：

- KMP、Android、Desktop、Web 工程入口；
- 共享 Vault 领域模型、key/规则校验、三级目录和递归搜索；
- V1 确定性密码生成、恢复配方和固定 SHA-256 测试向量；
- 五分钟绝对授权会话、操作许可和同步事件模型；
- 初始跨平台 Compose 用户列表、创建用户和密码库页面。

密码学依赖、平台安全存储、认证加密、真实生物识别、IndexedDB、S3 客户端和完整同步仍在实施中。当前构建产物不得用于保存真实密码。

## 构建

本项目不要求在宿主机安装 JDK、Gradle 或 Android Studio。Docker 构建容器包含 JDK/Gradle，Android 构建容器包含 Android SDK。

构建容器默认使用 Docker Desktop 的 `http.docker.internal:3128` 代理；如代理地址不同，可在宿主机设置 `DOCKER_BUILD_HTTP_PROXY`、`DOCKER_BUILD_HTTPS_PROXY` 和可选的 `DOCKER_BUILD_GRADLE_OPTS` 后执行以下命令。

执行共享测试、Desktop/Web 构建：

```text
docker compose run --rm builder :shared:desktopTest
docker compose run --rm builder :desktopApp:packageDistributionForCurrentOS
docker compose run --rm builder :webApp:wasmJsProductionExecutableCompileSync
```

执行 Android Debug 打包：

```text
docker compose run --rm android-builder
```

启动 Silo 测试 S3，并创建三个独立 bucket：

```text
docker compose up -d silo s3-init
docker compose run --rm s3-smoke
```

测试 S3 连接参数：

```text
endpoint:   http://localhost:9000
access key: hdpwd-test
secret key: hdpwd-test-password
region:     us-east-1
buckets:    hdpwd-s3-a / hdpwd-s3-b / hdpwd-s3-c
console:    http://localhost:9001
```

停止测试环境并删除容器：

```text
docker compose down
```

在依赖和平台能力验证完成前，不应将当前 V1 生成器视为最终生产加密实现。Silo 只用于本地测试，不得用于生产数据。
