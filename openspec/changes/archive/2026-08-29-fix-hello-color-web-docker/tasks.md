## 1. Windows Hello 闸门

- [x] 1.1 抽出 `DpapiProtector`（现有 Crypt32 与 label 前缀），密文格式保持不变
- [x] 1.2 定义可注入的 `WindowsHelloConsent`：查询 Hello 能力并请求用户在场验证
- [x] 1.3 本机 Windows spike：用 WinRT `UserConsentVerifier` 打通系统 Hello 弹窗（含取消/失败）
- [x] 1.4 将 `CheckAvailabilityAsync` 映射为 `AVAILABLE` / `NOT_ENROLLED` / `UNAVAILABLE`，非 Windows 恒为不可用
- [x] 1.5 实现 `DesktopWindowsHelloDpapiProvider`：`seal`/`open` 先 Hello 再 DPAPI；取消或失败抛错且不改密文
- [x] 1.6 Desktop 入口改用新 Provider；设置页与自动拉起路径继续按 `availability()` 隐藏开关或回退主密码
- [x] 1.7 用假 consent 覆盖：未过 Hello 不得 Unprotect、Hello 成功后往返、未录入不报告 AVAILABLE、取消回退主密码

## 2. 卡片满色背景

- [x] 2.1 实现 HEX → 不透明 Compose Color 与相对亮度对比前景（约 0.179 阈值）
- [x] 2.2 密码项/文件夹 Card 使用所存 HEX 为 `containerColor`、对比色为 `contentColor`，避免 tonal 染色
- [x] 2.3 移除 `TintedSurface` 半透明叠层；选色器预览保持满色
- [x] 2.4 为对比度选择与非法 HEX 回退补测试

## 3. Web nginx 静态托管

- [x] 3.1 确认 `:webApp:wasmJsBrowserDistribution` 发行目录，并在 Dockerfile 中固定拷贝路径
- [x] 3.2 新增 `docker/web.Dockerfile` 多阶段构建（Gradle 编 Wasm + nginx:alpine），复用现有构建代理变量
- [x] 3.3 nginx 监听 8080，配置 `application/wasm`、COOP/COEP，以 dist 为站点根
- [x] 3.4 `docker-compose.yml` 增加 `web` 服务并映射 `8080:8080`
- [x] 3.5 README 写明 `docker compose up --build web` 与 `http://localhost:8080`

## 4. 注释与回归

- [x] 4.1 为新增/改动的手写类型与函数补中文文档注释，Hello 闸门威胁模型与线程约束用行内注释说明
- [x] 4.2 跑共享测试及 Desktop 相关测试，确认 Web 无生物识别、会话与用户列表行为未改
