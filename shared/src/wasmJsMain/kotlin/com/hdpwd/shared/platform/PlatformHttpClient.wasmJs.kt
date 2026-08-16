package com.hdpwd.shared.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

/**
 * Web/Wasm 使用 JS 引擎。
 */
actual fun platformHttpClient(): HttpClient = HttpClient(Js) {
    expectSuccess = false
}
