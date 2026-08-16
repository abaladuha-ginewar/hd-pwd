package com.hdpwd.shared.platform

import io.ktor.client.HttpClient

/**
 * 各平台创建用于 S3 请求的 HttpClient。
 */
expect fun platformHttpClient(): HttpClient
