package com.hdpwd.shared.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

/**
 * Desktop 使用 CIO 引擎。
 */
actual fun platformHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = false
}
