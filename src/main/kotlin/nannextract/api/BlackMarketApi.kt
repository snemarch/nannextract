package nannextract.api

import com.google.gson.Gson
import nannextract.model.Author
import nannextract.model.BlogPostMeta
import com.google.gson.reflect.TypeToken
import nannextract.model.User
import okhttp3.*
import org.jsoup.Jsoup
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

	fun retrieveBlogPostListFor(author:Author) {
		val currentPage = 1

		while(true) {
			val (list, morePages) = retrieveBlogListPage(author.userId, currentPage)

			if(!morePages) {
				break
			}
		}
	}

	fun retrieveBlogListPage(uid:Int, pageNumber:Int) : Pair<List<BlogPostMeta>, Boolean> {
		val url = "http://blackmarket.dk/Blog?action=viewlist&view=list&uid=$uid&pageno=$pageNumber"
		val request = Request.Builder()
				.url(url)
				.get()
				.build()
		val response = client.newCall(request).execute()
		val dom = Jsoup.parse(response.body().string())

		return Pair(emptyList(), false)
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

	fun lookupUser(userName:String) : List<User>
	{
		val body = FormBody.Builder().add("action", "usernameautocompletelist").add("callback", "Jeg_er_en_robot").add("term", userName).build()
		val request = Request.Builder().url("http://blackmarket.dk/User").post(body).build()
		val response = client.newCall(request).execute()

		val responseBody = response.body().string()
		val startIndex = "Jeg_er_en_robot".length + 1
		val endIndex = responseBody.length - 2

		val json_list = responseBody.substring(startIndex, endIndex)

		// Hack to get gson deserialization to work
		val listType = object : TypeToken<List<User>>(){}.type
		val users:List<User> = Gson().fromJson<List<User>>(json_list, listType)

		return users
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
