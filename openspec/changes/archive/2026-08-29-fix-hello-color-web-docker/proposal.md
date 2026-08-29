## Why

Windows Desktop 把无提示的 DPAPI 当成「生物识别」，已登录即可解开 DeviceLEK，点选用户不必输入主密码或完成任何用户在场验证。密码项和文件夹的背景以 28% 透明叠在卡片底色上，和用户所选 HEX 对不上。Web 端目前只能在 Docker 里编译 Wasm，没有可在浏览器打开的托管服务，无法直接本地试跑。

## What Changes

- Desktop 生物识别改为 **Windows Hello 闸门 + DPAPI 封装**：`seal` / `open` 必须先通过系统 Hello（指纹、面容或 PIN），再使用现有 DPAPI 保护 DeviceLEK；Hello 不可用或未录入时不显示启用开关，只走主密码。
- 已有静默 DPAPI 密文格式不变，升级后只需在每次封装/解封装前增加 Hello 验证，不做密钥迁移。
- 密码项与文件夹卡片改为使用所存不透明 RGB 作为背景，前景色按相对亮度在深色/浅色之间选择，保证与选色器预览一致。
- 新增 Docker `web` 服务：多阶段构建 Wasm 发行物并由 nginx 静态托管，本机一条命令即可在浏览器打开。五分钟授权会话、用户列表不锁、Web 无生物识别均保持不变。

## Capabilities

### New Capabilities

无。本次是现有本机访问与卡片展示的修正，以及工程侧 Web 预览托管。

### Modified Capabilities

- `local-user-access`: Desktop 生物识别必须在用户通过 Windows Hello 后才能封装或解封装 DeviceLEK；仅操作系统登录态不得视为已验证。
- `vault-item-management`: 密码项与文件夹须以所存不透明 HEX 作为卡片背景展示，并根据背景亮度选择对比前景。

## Impact

- `DesktopWindowsDpapiProvider` 拆为 Hello 用户在场验证与 DPAPI 保护两层；`availability()` 改为询问 Hello 能力，不再仅因 Windows 就报告可用。
- 卡片 UI 去掉半透明叠色，改由 Card 使用所选色与对比前景；选色器预览保持满色。
- 新增 `docker/web.Dockerfile`、nginx 配置和 compose `web` 服务，README 补充本地浏览器访问方式。
- 不改变 DeviceLEK、恢复密码、Vault 数据密钥、备份或 S3 协议；DPAPI 密文格式保持兼容。
