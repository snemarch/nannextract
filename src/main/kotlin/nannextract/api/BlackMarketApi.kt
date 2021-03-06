package nannextract.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import nannextract.model.Author
import nannextract.model.BlogPostMeta
import nannextract.model.Contact
import nannextract.util.DateUtil
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import java.io.*
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.regex.Pattern

class BlackMarketApi {
	private val logger = LoggerFactory.getLogger(javaClass)

	val MAX_CONCURRENT_CONNECTIONS = 20

	val cookieStore = CookieStore()
	val client:OkHttpClient = OkHttpClient.Builder()
			.cookieJar(cookieStore)
			.connectTimeout(20, TimeUnit.SECONDS)
			.readTimeout(20, TimeUnit.SECONDS)
			.writeTimeout(20, TimeUnit.SECONDS)
			.build()

	init {
		client.dispatcher().maxRequests = MAX_CONCURRENT_CONNECTIONS
		client.dispatcher().maxRequestsPerHost = MAX_CONCURRENT_CONNECTIONS
	}

	fun login(user:String, password:String):Boolean {
		val body = FormBody.Builder().add("username", user).add("password", password).build()
		val request = Request.Builder()
				.url("http://blackmarket.dk/servlet/Authenticate")
				.post(body)
				.build()

		return client.newCall(request).execute().use { it.isSuccessful }
	}

	fun isLoggedIn():Boolean {
		// If we get redirected to the frontpage by trying to view the profile, we're not logged in. Unfortunately it
		// doesn't seem like OkHttp can disable redirect-follow without creating a new client, so we check isRedirect
		// on priorResponse, if present.
		val request = Request.Builder().url("http://blackmarket.dk/Profile?action=view").get().build()
		return client.newCall(request).execute().use {
			!(it.priorResponse()?.isRedirect ?: false)
		}
	}

	fun shutdown() {
		client.dispatcher().executorService().shutdown()
		client.dispatcher().executorService().awaitTermination(10, TimeUnit.SECONDS)
	}

	fun retrieveBlogPostListFor(userId:Int) : List<BlogPostMeta> {
		var currentPage = 1

		val blogMetaList = mutableListOf<BlogPostMeta>()

		while(true) {
			val list = retrieveBlogListPage(userId, currentPage++)
			if(list.isEmpty()) {
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
		val dom = response.use { Jsoup.parse(response.body().string()) }

		// This is the kind of selectors you get when dealing with horrible markup
		val areaOfInterest = dom.select("div.content > table table.pane > tbody")
		val noBlogs = areaOfInterest.select("> tr:eq(4) > td table > tbody > tr:eq(2) > td:contains(ingen blogs)")
		if (noBlogs.size != 0) {
			return emptyList()
		}

		val rows = areaOfInterest.select("> tr:eq(4) table > tbody > tr[onmouseover]")

		val posts = rows.map {
			val idAndTitle = idAndTitleMatcher.matcher(it.select("td:eq(0) > a").outerHtml())
			val dateString = it.select("td:eq(2)").text()
			val numViewsMatch = numViewsMatcher.matcher(it.select("td:eq(3)").text())

			if (!(idAndTitle.find() && numViewsMatch.find())) {
				throw RuntimeException("Couldn't parse row")
			}

			val id = idAndTitle.group(1).toInt()
			val title = idAndTitle.group(2)
			val numViews = numViewsMatch.group(1).toInt()

			BlogPostMeta(id, title, DateUtil.parseNaturalDate(Supplier { LocalDate.now() }, dateString), numViews)
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

	fun lookupAuthor(userName:String) : List<Author>
	{
		val body = FormBody.Builder().add("action", "usernameautocompletelist").add("callback", "Jeg_er_en_robot").add("term", userName).build()
		val request = Request.Builder().url("http://blackmarket.dk/User").post(body).build()
		val response = client.newCall(request).execute()

		// Could probably be done better
		val responseBody = response.use { response.body().string() }

		val startIndex = "Jeg_er_en_robot".length + 1
		val endIndex = responseBody.length - 2

		val json_list = responseBody.substring(startIndex, endIndex)

		// Hack to get gson deserialization to work
		val listType = object : TypeToken<List<Author>>(){}.type
		val users:List<Author> = Gson().fromJson<List<Author>>(json_list, listType)

		return users
	}

	fun retrieveBlogContent(blogId:Int, consumer:Consumer<Pair<Int, String>>)
	{
		val formBody = FormBody.Builder().add("action", "view").add("id", blogId.toString()).build()
		val request = Request.Builder().url("http://blackmarket.dk/Blog").post(formBody).build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				logger.error("Error retrieving blog#$blogId", e)
			}

			override fun onResponse(call: Call, response: Response) {
				val dom = response.use { Jsoup.parse(response.body().string()) }
				val blogContent = dom.select("table.pane:eq(0) > tbody > tr:eq(3) > td")

				// Removes surrounding <td> tags before returning
				val body = blogContent.toString().substring(4, blogContent.toString().length - 5)

				consumer.accept(Pair(blogId, body))
			}
		})
	}

	fun retrieveContacts(userId:Int, consumer:Consumer<List<Contact>>) {
		simpleAsync("http://blackmarket.dk/Friend?action=viewall&uid=$userId&view=list", Consumer {
			dom -> consumer.accept(Extractors.extractContacts(dom, Supplier { LocalDate.now() }))
		})
	}

	fun retrieveProfile(userId:Int, consumer:Consumer<String?>) {
		simpleAsync("http://blackmarket.dk/Profile?action=view&uid=$userId", Consumer {
			dom -> consumer.accept(Extractors.extractMainContent(dom))
		})
	}

	fun retrievePresentation(userId:Int, consumer:Consumer<String?>) {
		simpleAsync("http://blackmarket.dk/Presentation?action=view&uid=$userId", Consumer {
			dom -> consumer.accept(Extractors.extractMainContent(dom))
		})
	}

	fun simpleAsync(url:String, consumer:Consumer<Document>) {
		val request = Request.Builder().url(url).get().build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call?, e: IOException?) {
				logger.error("Error retrieving $url!", e)
			}

			override fun onResponse(call: Call, response: Response) {
				val dom = response.use { Jsoup.parse(response.body().string()) }
				consumer.accept(dom)
			}
		})
	}

	class CookieStore : CookieJar {
		@Transient
		private val notExpired = { cookie:Cookie -> cookie.expiresAt() > System.currentTimeMillis() }

		private val cookieStore:MutableSet<Cookie> = mutableSetOf()

		override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
			cookieStore.addAll(cookies)
		}

		override fun loadForRequest(url: HttpUrl): List<Cookie> {
			return cookieStore.filter(notExpired)
		}

		fun save(stream:OutputStream) {
			OutputStreamWriter(stream).use {
				it.write(Gson().toJson(this))
			}
		}

		fun load(stream:InputStream) {
			val blob = InputStreamReader(stream).use { it.readText() }
			val newCookies = Gson().fromJson(blob, CookieStore::class.java).cookieStore

			this.cookieStore.clear()
			this.cookieStore.addAll(newCookies.filter(notExpired))
		}

		fun dumpCookies() = cookieStore.forEach { println("Cookie: ${it.name()} = ${it.value()}, expires ${Date(it.expiresAt())}") }
	}
}
