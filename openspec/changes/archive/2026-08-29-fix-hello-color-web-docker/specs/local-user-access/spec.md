## ADDED Requirements

### Requirement: Desktop Windows Hello 用户在场
系统 MUST 在 Windows Desktop 将生物识别实现为：先通过系统 Windows Hello（指纹、面容或 PIN）完成用户在场验证，再使用 DPAPI 封装或解封装同一把 `DeviceLEK`。仅当前操作系统登录态 MUST NOT 视为已通过设备验证，也 MUST NOT 单独完成 `DeviceLEK` 的封装或解封装。Hello 不可用或当前用户未录入时，系统 MUST 将能力视为不可用或未录入，MUST NOT 显示生物识别启用选项，并使用本机主密码流程。Android 仍使用平台强生物识别提示；Web 仍不得提供生物识别。

#### Scenario: Hello 通过后解封装
- **WHEN** Windows 上已启用生物识别、Hello 可用，且用户在无有效授权会话时点选用户并完成系统 Hello 验证
- **THEN** 系统解封装 `DeviceLEK` 并允许进入该用户密码库

#### Scenario: 仅操作系统登录不得进入用户
- **WHEN** Windows 用户已登录操作系统、本机已启用生物识别封装，但用户未完成 Hello 验证也未输入主密码
- **THEN** 系统不得解密该用户密码库，也不得建立授权会话

#### Scenario: Hello 未录入
- **WHEN** 当前 Windows 用户尚未配置 Windows Hello
- **THEN** 系统不显示生物识别开关，验证仅接受本机主密码

#### Scenario: Hello 取消后改用主密码
- **WHEN** 系统自动发起 Hello 且用户取消或失败
- **THEN** 系统允许改用本机主密码，且不得关闭生物识别偏好或损坏 `DeviceLEK` 与用户封装

## MODIFIED Requirements

### Requirement: 生物识别解锁
系统 SHALL 在设备环境支持时允许用户在首次设置或设备设置中通过初始生物识别验证启用解锁。启用成功 MUST 封装设备级 `DeviceLEK` 并将本机默认验证方式设为生物识别。取消启用或关闭开关 MUST 使本机默认使用主密码。后续验证 MUST 允许生物识别和主密码相互回退。平台生物识别 MUST 要求用户在场（Android 为强生物识别提示，Windows Desktop 为 Windows Hello）；操作系统登录态或无提示的 DPAPI 解封装 MUST NOT 单独视为生物识别验证成功。

#### Scenario: 成功启用生物识别
- **WHEN** 用户选择启用且通过平台生物识别初始验证
- **THEN** 系统使用平台安全能力封装本机 `DeviceLEK`

#### Scenario: 生物识别不可用
- **WHEN** 生物识别因权限、硬件、环境变化或密钥失效而失败
- **THEN** 系统允许用户切换为本机主密码验证且不得损坏密码库或设备锁

#### Scenario: 平台不支持生物识别解密
- **WHEN** 当前平台不能稳定提供所需的硬件封装能力
- **THEN** 系统不显示不可用的启用选项并使用本机主密码流程

### Requirement: 验证方式默认与切换
系统 SHALL 在需要设备验证时根据本机 `preferBiometric` 选择默认方式：若为真且生物识别可用，MUST 自动发起需要用户在场的生物识别并提供切换到主密码的入口；否则 MUST 显示主密码输入。自动发起 MUST 弹出平台验证界面，MUST NOT 因操作系统已登录而静默成功。生物识别失败、取消或密钥失效 MUST 允许改用主密码，且 MUST NOT 悄悄关闭偏好或损坏数据。

#### Scenario: 默认生物识别并可改用主密码
- **WHEN** 本机默认验证方式为生物识别、能力可用且用户触发需要 `DeviceLEK` 的操作
- **THEN** 系统自动发起生物识别验证，并允许用户改为输入主密码

#### Scenario: 默认主密码
- **WHEN** 本机默认验证方式为主密码或当前平台无生物识别
- **THEN** 系统显示主密码输入且不自动发起生物识别
