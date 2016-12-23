package nannextract.api

import okhttp3.*

class BlackMarketApi {
	val cookieStore = CookieStore()
	val client:OkHttpClient = OkHttpClient.Builder().cookieJar(cookieStore).build()

	fun login(user:String, password:String):Boolean {
		val body = FormBody.Builder().add("username", user).add("password", password).build()
		val request = Request.Builder()
				.url("http://blackmarket.dk/servlet/Authenticate")
				.post(body)
				.build()

		return client.newCall(request).execute().isSuccessful
	}

	fun dumpCookies() {
		cookieStore.dumpCookies()
	}

	class CookieStore : CookieJar {
		private val cookieStore:MutableSet<Cookie> = mutableSetOf()

		override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
			cookieStore.addAll(cookies)
		}

		override fun loadForRequest(url: HttpUrl): List<Cookie> {
			return cookieStore.filter { it -> it.expiresAt() < System.currentTimeMillis() }
		}

		fun dumpCookies() = cookieStore.forEach { println("Cookie: ${it.name()} = ${it.value()}") }
	}
}
