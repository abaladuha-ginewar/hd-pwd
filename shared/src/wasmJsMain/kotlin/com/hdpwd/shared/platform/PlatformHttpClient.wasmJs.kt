package com.hdpwd.shared.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

/**
 * Web/Wasm 使用 JS 引擎；固定 identity 编码以稳定 S3 签名。
 */
actual fun platformHttpClient(): HttpClient = HttpClient(Js) {
    expectSuccess = false
    defaultRequest {
        header(HttpHeaders.AcceptEncoding, "identity")
    }
}
