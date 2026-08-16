package com.hdpwd.shared.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import okhttp3.OkHttpClient

/**
 * Android 使用 OkHttp 引擎；禁用自动 gzip，避免干扰 S3 SigV4。
 */
actual fun platformHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    engine {
        preconfigured = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept-Encoding", "identity")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
    defaultRequest {
        header(HttpHeaders.AcceptEncoding, "identity")
    }
}
