package nannextract

import com.google.gson.Gson
import nannextract.api.BlackMarketApi
import nannextract.util.generateProgressBar
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

private val COOKIEJAR_FILENAME = "cookiejar.json"

object Main {
	val logger = LoggerFactory.getLogger(javaClass)

	@JvmStatic
	fun main(args: Array<String>) {
		if (args.size < 3) {
			print("Usage: nannextract <username> <password> <blog author>")
			return
		}

		val api = BlackMarketApi()
		if (!login(api, username = args[0], password = args[1])) {
			println("Error logging in")
			return
		}

		if (args[2] == "@scrape") {
			if(args.size < 5) {
				println("@scrape requires starting and ending userids to scrape")
				return
			}
			val range = args[3].toInt() .. args[4].toInt()
			sweepingScrape(api, range)
		} else {
			extractBlogs(api, author = args[2])
		}

		api.shutdown()
	}

	private fun login(api: BlackMarketApi, username: String, password: String): Boolean {
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

	private fun extractBlogs(api: BlackMarketApi, author: String): Boolean {
		logger.info("Starting blog extraction for $author")

		val authors = api.lookupAuthor(author)
		if (authors.isEmpty()) {
			logger.info("Author not found")
			return false
		}

		if (authors.size > 1) {
			logger.info("Multiple authors found - using ${authors.first()}")
		}

		logger.info("Retrieving list of blogs")
		val blogList = api.retrieveBlogPostListFor(authors.first())

		logger.info("Retrieving ${blogList.size} blog entries")

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
		logger.info("\nDone after ${duration.toMillis() / 1000.0}s, shutting down")
		return true
	}

	/**
	 * Scrape profile, presentation and contacts for a range of users.
	 */
	fun sweepingScrape(api: BlackMarketApi, userIdRange: IntRange) {
		logger.info("Starting scrape of userId range $userIdRange")

		var lastStatus = Instant.now()
		for (userId in userIdRange) {
			createOutputPath("output/${bin(userId)}")

			api.retrieveProfile(userId, Consumer {
				content -> if (content != null) {
					File("output/${bin(userId)}/$userId.profile.html").writeText(content)
				}
			})

			api.retrievePresentation(userId, Consumer {
				content -> if (content != null) {
					File("output/${bin(userId)}/$userId.presentation.html").writeText(content)
				}
			})

			api.retrieveContacts(userId, Consumer {
				content -> if (!content.isEmpty()) {
					File("output/${bin(userId)}/$userId.contacts.json").writeText(Gson().toJson(content))
				}
			})

			// Print status about every 250ms. We do it with a is-time-past check since we don't want to add artificial
			// delays to the scraping process...
			if(Instant.now().isAfter(lastStatus.plusMillis(250))) {
				print("\rQueued: $userId of ${userIdRange.last}    ")
				lastStatus = Instant.now()
			}

			// ...We do, however, apply a bit of pushback to avoid using a ton of heap on queued calls.
			while (api.client.dispatcher().queuedCallsCount() > api.MAX_CONCURRENT_CONNECTIONS * 2) {
				Thread.sleep(250)
			}
		}
		println()

		waitForWorkers(api)
		logger.info("Scrape done")
	}

	fun waitForWorkers(api: BlackMarketApi) {
		println("Waiting for work to be done")
		while (true) {
			val running = api.client.dispatcher().runningCallsCount()
			val pending = api.client.dispatcher().queuedCallsCount()

			print("\rRunning: $running, pending: $pending          ")
			if (running == 0 && pending == 0) {
				break
			}

			Thread.sleep(250)
		}
	}

	// We use the ConcurrentHashMap to only hit the filesystem for output-folder creation the first time we see a path.
	// Possibly a premature optimization :-)
	val created = ConcurrentHashMap<String, Boolean>()

	private fun createOutputPath(path: String) {
		created.computeIfAbsent(path, {
			Files.createDirectories(Paths.get(path))
			true
		})
	}

	/**
	 * Selects a "bin" directory to put output files in based on userid - we do this to avoid dumping potentially tens of
	 * thousands of files in a single directory.
	 */
	fun bin(num: Int): String = String.format(Locale.US, "%06d", ((num / 100) * 100))
}
