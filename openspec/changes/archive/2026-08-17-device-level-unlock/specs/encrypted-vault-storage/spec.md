## ADDED Requirements

### Requirement: 设备锁与用户索引分离
系统 MUST 将设备锁记录（`DeviceLEK` 包装、设备锁世代、生物识别偏好与封装）保存在独立于用户索引的本机存储中。用户索引 MUST NOT 包含主密码、`DeviceLEK`、生物识别材料或恢复密码密文。未通过设备验证时系统 SHALL 仍可读取用户名列表。

#### Scenario: 未验证时读取用户列表
- **WHEN** 本机已有设备锁但当前没有授权会话
- **THEN** 系统可显示用户名列表，但不能读取任何密码库业务内容或解开任何恢复密码封装

## MODIFIED Requirements

### Requirement: 解密内存生命周期
系统 SHALL 仅在用户已解密期间在内存维护当前密码库业务状态和搜索索引，并在返回用户列表、取消验证、会话锁定或删除用户时清除这些引用并尽力覆盖敏感二进制缓冲区。授权会话到期 MUST 清除 `DeviceLEK`；若当时密码库已解密，MUST 同时移除解密 Vault 并返回用户列表。返回用户列表本身 MUST 清除解密 Vault，但不得仅因此清除仍在有效期内的 `DeviceLEK`。

#### Scenario: 会话锁定
- **WHEN** 当前授权会话失效
- **THEN** 系统移除 `DeviceLEK`、解密 Vault、搜索索引、临时恢复密码、用途密钥和生成密码，并返回用户列表
