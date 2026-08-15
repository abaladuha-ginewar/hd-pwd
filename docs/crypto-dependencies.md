# 跨平台密码学依赖矩阵

## 选型结论

| 平台 | 依赖 | 原语 | 状态 |
| --- | --- | --- | --- |
| Android/JVM/Desktop | `com.diglol.crypto:crypto:0.2.0` | Argon2、HMAC、XChaCha20-Poly1305、安全随机数 | 已加入平台源码集，待 API 封装和向量验证 |
| Web/Wasm | `libsodium-wrappers-sumo@0.8.4` | `crypto_pwhash` Argon2id、XChaCha20-Poly1305、随机数 | 已加入 Wasm npm 依赖，待 Kotlin/JS interop 封装和向量验证 |
| 全平台 | `kotlinx-serialization-cbor:1.8.1` | 恢复配方和协议载荷 | 已接入 |

## 选择理由

- Diglol Crypto 是 Kotlin Multiplatform 库，包含 Argon2 与 XChaCha20-Poly1305，但当前公开平台矩阵不包含 Wasm，因此不能放入 `commonMain`。
- libsodium.js 通过 Emscripten 提供浏览器 Wasm/JavaScript 实现；`sumo` 版本才暴露完整 `crypto_pwhash_*` Argon2id API，同时提供 XChaCha20-Poly1305。
- 两端都通过共享 `CryptoProvider` 门面接入，领域层不直接依赖具体库。

## 安全约束

- 依赖版本必须锁定并在 CI 中执行依赖漏洞、许可证和供应链检查。
- 生产加解密只能使用这些依赖的认证 API，不得继续使用手写 SHA/HMAC 作为 Vault、备份或同步数据加密实现。
- V1 密码生成测试向量保持不变；数据 KDF 参数与生成 KDF 参数必须分离。
- Web 使用 `await sodium.ready` 后才允许调用 libsodium 函数，失败时必须拒绝敏感操作。
