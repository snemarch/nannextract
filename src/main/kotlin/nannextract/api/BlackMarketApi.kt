package nannextract.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.*

class BlackMarketApi {
	val cookieStore = CookieStore()
	val client:OkHttpClient = OkHttpClient.Builder().cookieJar(cookieStore).build()
	var isLoggedIn = false

	fun login(user:String, password:String):Boolean {
		val body = FormBody.Builder().add("username", user).add("password", password).build()
		val request = Request.Builder()
				.url("http://blackmarket.dk/servlet/Authenticate")
				.post(body)
				.build()

		isLoggedIn = client.newCall(request).execute().isSuccessful
		return isLoggedIn
	}

	fun dumpCookies() {
		cookieStore.dumpCookies()
	}

	fun loadCookies(stream:InputStream) {
		cookieStore.load(stream)
	}

	fun saveCookies(stream:OutputStream) {
		cookieStore.save(stream)
	}

	class CookieStore : CookieJar {
		private val cookieStore:MutableSet<Cookie> = mutableSetOf()

		override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
			cookieStore.addAll(cookies)
		}

		override fun loadForRequest(url: HttpUrl): List<Cookie> {
			return cookieStore.filter { it -> it.expiresAt() < System.currentTimeMillis() }
		}

		fun save(stream:OutputStream) {
			OutputStreamWriter(stream).use {
				it.write(Gson().toJson(this))
			}
		}

		fun load(stream:InputStream) {
			InputStreamReader(stream).use {
				val blob = it.readText()
				val newCookies = Gson().fromJson(blob, CookieStore::class.java).cookieStore

				this.cookieStore.clear()
				this.cookieStore.addAll(newCookies)
			}

		}

		fun dumpCookies() = cookieStore.forEach { println("Cookie: ${it.name()} = ${it.value()}, expires ${Date(it.expiresAt())}") }
	}
}
