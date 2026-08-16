package com.hdpwd.shared.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android 使用 OkHttp 引擎。
 */
actual fun platformHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
}
