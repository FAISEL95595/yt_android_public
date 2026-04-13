package com.faisel.ytdlf

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val cookies = CookieManager.getInstance().getCookie(url)
                if (cookies != null && cookies.contains("SID")) {
                    saveCookies(cookies)
                    finish()
                }
            }
        }
        webView.loadUrl("https://accounts.google.com/ServiceLogin?service=youtube")
    }

    private fun saveCookies(cookieString: String) {
        val file = File(getExternalFilesDir(null), "cookies.txt")
        val builder = StringBuilder("# Netscape HTTP Cookie File\n")
        cookieString.split("; ").forEach {
            val parts = it.split("=")
            if (parts.size >= 2) {
                builder.append(".youtube.com\tTRUE\t/\tTRUE\t0\t${parts[0]}\t${parts[1]}\n")
            }
        }
        file.writeText(builder.toString())
    }
}