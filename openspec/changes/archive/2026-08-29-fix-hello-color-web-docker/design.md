## Context

三端共享设备锁与 Vault UI。当前 Windows Desktop 的 `DesktopWindowsDpapiProvider` 在任意 Windows 上都报告 `AVAILABLE`，并用 `CryptProtectData` / `CryptUnprotectData` 封装 DeviceLEK。DPAPI 只绑定当前 Windows 登录态，**不弹出 Hello、指纹或 PIN**。用户一旦在设置中启用「生物识别」，点选用户时 `DeviceAuthDialog` 会自动 `open()`，于是无提示进入密码库。Android 则通过 `BiometricPrompt` + Keystore 用户认证密钥真正要求在场。

卡片颜色已按不透明 HEX 写入 Vault，但 `TintedSurface` 以 28% 透明叠在 Material3 Card 底色上，列表观感与选色器预览不一致。

Web 可在 Docker 构建容器中执行 `:webApp:wasmJsProductionExecutableCompileSync`，没有 HTTP 服务，Wasm 也不能用 `file://` 打开，本机无法直接在浏览器试跑。

约束：不改 DeviceLEK / 恢复密码 / 备份 / S3；Web 仍无生物识别；五分钟会话与用户列表不锁保持原规格；Windows Hello 闸门是应用内策略，不是 Android Keystore 那种密码学绑定。

## Goals / Non-Goals

**Goals:**

- Desktop 生物识别：先 Windows Hello（指纹、面容或 PIN）用户在场，再 DPAPI 封装/解封装同一把 DeviceLEK。
- Hello 不可用或未录入时不显示启用开关，验证只走主密码。
- 密码项与文件夹卡片以所存不透明 HEX 为背景，按相对亮度选择深色或浅色前景。
- 提供 `docker compose up --build web`，nginx 静态托管 Wasm 发行物，本机浏览器可打开。

**Non-Goals:**

- 不把 Hello 做成 TPM/NCrypt 密钥绑定，也不引入 WebAuthn。
- 不收紧五分钟授权会话，不锁定用户列表。
- 不为 Web 增加生物识别。
- 不强制 Windows Hello 排除 PIN（与 Android `BIOMETRIC_STRONG` 不完全对齐，接受平台 Hello 定义）。
- 不做 webpack 热更新或开发服务器；Web Docker 只做静态托管。
- 不为本次解决浏览器访问 Silo 的 CORS（可后续再加）。

## Decisions

### 1. Hello 闸门 + 现有 DPAPI 密文，而不是替换封装格式

将现有 Provider 拆成两层：

1. `WindowsHelloConsent`：WinRT `UserConsentVerifier`。`CheckAvailabilityAsync` 映射到 `BiometricAvailability`（Available → `AVAILABLE`，NotConfiguredForUser → `NOT_ENROLLED`，其余 → `UNAVAILABLE`）。`RequestVerificationAsync` 在 `seal` / `open` 之前弹出系统 Hello。
2. `DpapiProtector`：保留现有 `Crypt32Util` 与 label 前缀，密文格式不变。

`DesktopWindowsHelloDpapiProvider` 组合二者：非 Windows 或 Hello 非 Available 时 `availability()` 不得为 `AVAILABLE`。取消或失败抛错，现有验证 UI 回退主密码，且不得悄悄把 `preferBiometric` 改为关。

备选是 Desktop 直接报告 `UNAVAILABLE`（去掉假开关）。这能阻止静默进入，但无法使用已有 Hello 硬件。用户明确要求闸门而不是取消能力。

备选是 TPM/NCrypt 或 KeyCredentialManager 做密码学绑定。更接近 Android，但依赖、测试环境和 MSI 体积都更大，本次不做。

安全含义：闸门只约束本应用调用路径。同 Windows 用户令牌下直接调用 `CryptUnprotectData` 仍可能解开密文。对抗的是已登录桌面旁的操作者，不是能跑任意代码的进程。

### 2. 启用与解锁都要过 Hello，已有密文不迁移

首次勾选启用、设置中打开开关、以及后续自动拉起生物识别，都必须 `RequestVerificationAsync` 成功后才能 DPAPI 操作。已写入的静默 DPAPI 密文无需改格式：升级后同一密文在 Hello 通过后再 `Unprotect`。Linux CI 与非 Windows Desktop 仍走 `UNAVAILABLE`；单元测试用可注入的 consent 假实现，避免真实弹窗。

### 3. 卡片满色背景 + 相对亮度前景

去掉 `TintedSurface` 的 alpha 叠层。`Card` 使用所选 HEX 作为 `containerColor`，自定义色不要走 `surface` 以免被 tonal elevation 染色。前景用 sRGB 相对亮度（阈值约 0.179）在接近黑/白之间选择，Text 与 Icon 继承 `LocalContentColor`。选色器预览已是满色，列表与之对齐。

备选是提高叠色比例或左侧色条。前者仍不是「所设之色」，后者改布局。用户要求满色背景。

### 4. 多阶段 Docker + nginx 静态托管

新增 `docker/web.Dockerfile`：

- 构建阶段复用 JDK 17 Gradle 镜像，执行 `:webApp:wasmJsBrowserDistribution`，沿用现有构建代理环境变量。
- 运行阶段 `nginx:alpine`，拷贝 dist（Kotlin/Wasm 发行目录，实施时核对实际路径），监听 8080。
- nginx 声明 `application/wasm`，并设置 `Cross-Origin-Opener-Policy: same-origin` 与 `Cross-Origin-Embedder-Policy: require-corp`，避免部分浏览器拦截 Wasm / libsodium。

`docker-compose.yml` 增加 `web` 服务。`README` 写明 `docker compose up --build web` 与 `http://localhost:8080`。改代码需重建镜像。现有 `builder` 编译任务可保留给 CI。

## Risks / Trade-offs

- [WinRT `UserConsentVerifier` 需在能显示 UI 的线程调用，JNA 绑定易出错] → 先做 Windows 本机 spike 打通弹窗，再接入 Provider；consent 接口可注入以便测试。
- [Hello 闸门不是密码学绑定] → 在设计与注释中写明威胁模型；不宣称与 Android Keystore 同等。
- [Windows Hello 含 PIN，Android 仅强生物识别] → 接受平台差异，规格写明 Desktop 以系统 Hello 为准。
- [满色背景导致部分色板对比度临界] → 用相对亮度选前景；色板十色与自定义 RGB 都走同一函数。
- [Wasm 发行目录随 Kotlin 插件变化] → Dockerfile 以 Gradle 任务输出为准，实施时确认路径并写进注释。
- [静态镜像不含热更新，首次构建慢] → 文档说明需 `--build`；Gradle 依赖尽量利用镜像层缓存。

## Migration Plan

1. 无 Hello 或未启用生物识别：行为与现在主密码路径相同。
2. 已启用静默 DPAPI：升级后密文仍有效；下次生物识别路径先弹 Hello，取消则主密码。
3. 卡片颜色：Vault 中 HEX 不变，仅展示变化，无需数据迁移。
4. Web Docker：纯新增服务，不影响现有 builder / Android / Silo。
5. 回滚：恢复旧 Provider 后旧 DPAPI 密文仍可解；卡片可再叠透明层；去掉 `web` 服务即可。

## Open Questions

无。Hello 含 PIN、闸门而非密钥绑定、满色背景、nginx 静态托管均已在探索中确定。
