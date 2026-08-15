## Why

用户需要一个可在 Android、Windows 和 Web 上使用的本地优先密码管理器：密码库及备份始终加密，子密码无需持久化，并且在全部密码库数据不可用时仍可凭恢复密码、key 和恢复配方确定性重建。当前仓库尚无应用实现，需要先建立完整的跨平台产品能力、安全边界和可演进的数据协议。

## What Changes

- 新建 Kotlin Multiplatform 跨平台应用，覆盖 Android、Windows Desktop 和 Web，并提供响应式密码库界面。
- 支持同一设备上的多个本地用户，每个用户拥有独立密码库、恢复密码、本机主密码、生物识别封装和五分钟授权会话。
- 使用本机 `LocalEnvelopeKey` 保护恢复密码；授权期内只缓存该密钥，恢复密码及其派生密钥仅在单次操作期间临时存在。
- 根据恢复密码、区分大小写的唯一 key、规范化密码规则和永久兼容的算法版本确定性生成子密码，并支持复制恢复配方。
- 支持三级目录、密码项和文件夹编辑、自定义标签与颜色、当前目录递归搜索及多端响应式展示。
- 为每个用户实时维护独立的加密本地密码库；Windows/Android 使用本地文件，Web 使用 IndexedDB。
- 支持不包含用户名和本机解锁材料的加密 `.dat` 备份导入导出。
- 支持多个兼容 S3 的双向同步副本，经过五秒静默期后同步，并将从任一副本合并的数据传播到其他全部副本。
- 提供版本化增量、不可变对象身份、冲突检测、删除墓碑、快照压缩和同步状态提示。
- 对密码显示、剪贴板、日志、网络传输、后台锁定及平台退出限制实施安全控制。

## Capabilities

### New Capabilities

- `local-user-access`: 本地多用户、恢复密码、本机主密码、生物识别、本机密钥封装、授权会话及全库迁移。
- `deterministic-password-generation`: 版本化确定性密码生成、密码规则、key 约束、恢复配方及无数据恢复。
- `vault-item-management`: 密码项、标签、文件夹、颜色、三级目录、编辑删除及响应式卡片交互。
- `vault-search-navigation`: 当前目录递归搜索、层级导航、排序与多设备响应式布局。
- `encrypted-vault-storage`: 独立用户 Vault、认证加密、实时持久化、原子文件写入、IndexedDB 存储及内存清理。
- `backup-portability`: 自包含加密备份、导入导出、文件命名、格式版本及恢复校验。
- `s3-multireplica-sync`: 多 S3 双向副本、增量传播、冲突合并、墓碑、快照、重试及状态反馈。
- `sensitive-data-interaction`: 临时密码显示、安全剪贴板、敏感日志约束、后台隐藏及平台生命周期保护。

### Modified Capabilities

无。

## Impact

- 新增完整的 Kotlin Multiplatform 工程、共享领域模型、Compose Multiplatform UI 和 Android/Windows/Web 平台入口。
- 引入经过审计的密码学实现、内存困难 KDF、认证加密、平台生物识别适配和安全随机数能力。
- 新增 Android/Windows 文件存储、Web IndexedDB、剪贴板和生命周期平台适配。
- 新增兼容 S3 的 HTTP/签名客户端、云厂商预设、多副本同步协议及加密备份格式。
- 安全协议、生成算法和备份格式一经发布需要保持版本兼容，并配套固定测试向量和跨平台一致性测试。
