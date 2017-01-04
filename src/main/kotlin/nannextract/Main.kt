package nannextract

import nannextract.api.BlackMarketApi
import nannextract.util.generateProgressBar
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.function.Consumer

private val COOKIEJAR_FILENAME = "cookiejar.json"
fun main(args : Array<String>) {
	if(args.size < 3) {
		print("Usage: nannextract <username> <password> <blog author>")
		return
	}

	val api = BlackMarketApi()
	if (!login(api, username = args[0], password = args[1])) {
		println("Error logging in")
		return
	}

	if (extractBlogs(api, author = args[2])) return

	api.shutdown()
}

private fun login(api: BlackMarketApi, username:String, password:String): Boolean {
	val cookieJar = File(COOKIEJAR_FILENAME)
	if (cookieJar.exists()) {
		println("Reading cookies")
		FileInputStream(cookieJar).use {
			api.loadCookies(it)
		}
	}

	if (!api.isLoggedIn()) {
		println("Cookies expired or not present, logging in")

		val success = api.login(username, password)
		if (!success) {
			println("Error logging in")
			return false
		}

		FileOutputStream(File(COOKIEJAR_FILENAME)).use { api.saveCookies(it) }
	}

	return api.isLoggedIn()
}

private fun extractBlogs(api: BlackMarketApi, author:String): Boolean {
	val authors = api.lookupAuthor(author)
	if (authors.isEmpty()) {
		println("Author not found")
		return false
	}

	if (authors.size > 1) {
		println("Multiple authors found - using ${authors.first()}")
	}

	println("Retrieving list of blogs")
	val blogList = api.retrieveBlogPostListFor(authors.first())

	println("Retrieving ${blogList.size} blog entries")

	val beforeTime = Instant.now()

	Files.createDirectories(Paths.get("output"))
	for (blog in blogList) {
		api.retrieveBlogContent(blog.id, Consumer {
			val filename = "output/${blog.date} - ${blog.id}.html"
			val body = it.second

			FileOutputStream(File(filename)).use {
				OutputStreamWriter(it).use {
					it.write("${blog.title} - ${blog.date} - ${blog.numViews} views\n\n")
					it.write(body)
				}
			}
		})
	}

	println("Waiting for work to be done")
	while (true) {
		val running = api.client.dispatcher().runningCallsCount()
		val pending = api.client.dispatcher().queuedCallsCount()

		val total = blogList.size
		val percentDone = (total.toDouble() - pending) / total
		val amountDone = total - pending
		val progressBar = generateProgressBar(amountDone, total)

		print("\r[$progressBar] ${Math.round(percentDone * 100)}% $amountDone/$total")
		if (running == 0 && pending == 0) {
			break
		}

		Thread.sleep(250)
	}

	val duration = Duration.between(beforeTime, Instant.now())
	println("\nDone after ${duration.toMillis() / 1000.0}s, shutting down")
	return true
}
