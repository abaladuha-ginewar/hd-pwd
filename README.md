# HD Password

基于 Kotlin Multiplatform 的本地优先密码管理器，支持 Android、Windows Desktop 和 Web。

## 功能概览

- 本机多用户：独立恢复密码、本机主密码、可选生物识别（Android / Windows DPAPI），五分钟绝对授权会话
- V1 确定性密码生成与恢复配方；无 Vault 时仍可凭恢复密码与规则重建子密码
- 加密 Vault：认证加密本地持久化（Android/Windows 私有文件；Web 使用 localStorage）
- 三级目录、标签、颜色、递归搜索与响应式 Compose UI
- 加密 `.dat` 备份导入导出（不含用户名与本机解锁材料）
- 多 S3 兼容副本双向同步（静默调度、增量合并、冲突与墓碑）

规格见 `openspec/specs/`。首版变更已归档于 `openspec/changes/archive/2026-08-16-build-kmp-password-manager/`。

## 构建

本项目不要求在宿主机安装 JDK、Gradle 或 Android Studio。Docker 构建容器包含 JDK/Gradle，Android 构建容器包含 Android SDK。

构建容器默认使用 Docker Desktop 的 `http.docker.internal:3128` 代理；如代理地址不同，可在宿主机设置 `DOCKER_BUILD_HTTP_PROXY`、`DOCKER_BUILD_HTTPS_PROXY` 和可选的 `DOCKER_BUILD_GRADLE_OPTS` 后执行以下命令。

共享测试与 Desktop / Web 构建：

```text
docker compose run --rm builder :shared:desktopTest
docker compose run --rm builder :desktopApp:packageDistributionForCurrentOS
docker compose run --rm builder :webApp:wasmJsProductionExecutableCompileSync
```

Android Debug 打包：

```text
docker compose run --rm android-builder
```

产物路径示例：`androidApp/build/outputs/apk/debug/hd-pwd-debug.apk`（可用 `adb install -r` 安装）。

## 本地 S3 测试

启动 Silo 测试 S3，并创建三个独立 bucket：

```text
docker compose up -d silo s3-init
docker compose run --rm s3-smoke
```

连接参数：

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

Silo 仅用于本地联调，不得存放生产数据。正式使用前请自行评估密钥备份、同步与平台能力降级策略。
