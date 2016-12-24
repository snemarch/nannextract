package nannextract.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import nannextract.model.Author
import nannextract.model.BlogPostMeta
import nannextract.model.User
import nannextract.util.DateUtil
import okhttp3.*
import org.jsoup.Jsoup
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import java.util.regex.Pattern

class BlackMarketApi {
	val maxIdleConnections = 10
	val cookieStore = CookieStore()
	val client:OkHttpClient = OkHttpClient.Builder()
			.cookieJar(cookieStore)
			.connectionPool(ConnectionPool(maxIdleConnections, 5, TimeUnit.MINUTES))
			.build()
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

	fun retrieveBlogPostListFor(author:Author) : List<BlogPostMeta> {
		var currentPage = 1

		val blogMetaList = mutableListOf<BlogPostMeta>()

		while(true) {
			val list = retrieveBlogListPage(author.userId, currentPage++)
			if(list.isEmpty()){
				break
			}

			blogMetaList.addAll(list)
		}

		return blogMetaList
	}

	private val idAndTitleMatcher = Pattern.compile("<a href=\".*?id=(\\d+).*\" title=\"(.*)\">.*</a>")
	private val numViewsMatcher = Pattern.compile("(\\d+)")

	private fun retrieveBlogListPage(userId:Int, pageNumber:Int) : List<BlogPostMeta> {
		val url = "http://blackmarket.dk/Blog?action=viewlist&view=list&uid=$userId&pageno=$pageNumber"
		val request = Request.Builder()
				.url(url)
				.get()
				.build()
		val response = client.newCall(request).execute()

		val dom = Jsoup.parse(response.body().string())

		// This is the kind of selectors you get when dealing with horrible markup
		val areaOfInterest = dom.select("div.content > table table.pane > tbody")
		val noBlogs = areaOfInterest.select("> tr:eq(4) > td table > tbody > tr:eq(2) > td:contains(ingen blogs)")
		if (noBlogs.size != 0)
		{
			return emptyList()
		}

		val rows = areaOfInterest.select("> tr:eq(4) table > tbody > tr[onmouseover]")

		val posts = rows.map {
			it ->
				val idAndTitle = idAndTitleMatcher.matcher(it.select("td:eq(0) > a").outerHtml())
				val dateString = it.select("td:eq(2)").text()
				val numViewsMatch = numViewsMatcher.matcher(it.select("td:eq(3)").text())

				if(! (idAndTitle.find() && numViewsMatch.find() )) {
					throw RuntimeException("Couldn't parse row")
				}

				val id = idAndTitle.group(1).toInt()
				val title = idAndTitle.group(2)
				val numViews = numViewsMatch.group(1).toInt()

				BlogPostMeta(id, title, DateUtil.parseNaturalDate(Supplier{ LocalDate.now() }, dateString), numViews)
		}

		return posts
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

		// Could probably be done better
		val responseBody = response.body().string()
		val startIndex = "Jeg_er_en_robot".length + 1
		val endIndex = responseBody.length - 2

		val json_list = responseBody.substring(startIndex, endIndex)

		// Hack to get gson deserialization to work
		val listType = object : TypeToken<List<User>>(){}.type
		val users:List<User> = Gson().fromJson<List<User>>(json_list, listType)

		return users
	}

	fun retrieveBlogContent(ID:Int) : String
	{
		val body = FormBody.Builder().add("action", "view").add("id", ID.toString()).build()
		val request = Request.Builder().url("http://blackmarket.dk/Blog").post(body).build()
		val response = client.newCall(request).execute()

		val dom = Jsoup.parse(response.body().string())

		val blogContent = dom.select("table.pane:eq(0) > tbody > tr:eq(3) > td")

		// Removes surrounding <td> tags before returning
		return blogContent.toString().substring(4,blogContent.toString().length - 5)
	}


	class CookieStore : CookieJar {
		private val cookieStore:MutableSet<Cookie> = mutableSetOf()

		override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
			cookieStore.addAll(cookies)
		}

		override fun loadForRequest(url: HttpUrl): List<Cookie> {
			return cookieStore.filter { it -> (it.expiresAt() > System.currentTimeMillis()) }
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
