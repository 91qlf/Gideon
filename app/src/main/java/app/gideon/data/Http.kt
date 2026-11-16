package app.gideon.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object Http {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun request(url: String, accept: String = "*/*"): Request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept", accept)
        .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.6")
        .build()
}
