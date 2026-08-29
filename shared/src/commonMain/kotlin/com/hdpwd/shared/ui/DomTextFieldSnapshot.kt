package com.hdpwd.shared.ui

/**
 * Web 从真实 DOM 读取当前输入，避免 Compose 状态落后于中文输入法。
 * 其他平台返回 null，调用方回退到 Compose 状态。
 */
internal expect fun snapshotDomTextField(label: String): String?
