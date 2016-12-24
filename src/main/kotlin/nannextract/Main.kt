package nannextract

import nannextract.api.BlackMarketApi
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.function.Consumer

fun main(args : Array<String>) {
	val COOKIEJAR_FILENAME = "cookiejar.json"

	if(args.size < 3) {
		print("Usage: nannextract <username> <password> <blog author>")
		return
	}

	val api = BlackMarketApi()

	val cookieJar = File(COOKIEJAR_FILENAME)
	if(cookieJar.exists()) {
		println("Reading cookies")
		FileInputStream(cookieJar).use {
			api.loadCookies(it)
		}
	} else {
		println("Logging in")
		val userName = args[0]
		val password = args[1]

		val success = api.login(userName, password)
		if(success) {
			println("Login successful! - dumping cookies")
			api.dumpCookies()
		} else {
			println("Error logging in")
			return
		}

		FileOutputStream(File(COOKIEJAR_FILENAME)).use { api.saveCookies(it) }
	}

	val authors = api.lookupAuthor(args[2])
	if(authors.isEmpty()) {
		println("Author not found")
		return
	}
	if(authors.size > 1) {
		println("Multiple authors found - using ${authors.first()}")
	}

	println("Retrieving list of blogs")
	val blogList = api.retrieveBlogPostListFor(authors.first())

	println("Retrieving blog entries")

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

	println("Waiting for all threads to shut down")
	while(true) {
		val running = api.client.dispatcher().runningCallsCount()
		val pending = api.client.dispatcher().queuedCallsCount()

		print("\rRunning: $running, pending: $pending          ")
		if(running == 0 && pending == 0) {
			break
		}

		Thread.sleep(250)
	}

	val duration = Duration.between(beforeTime, Instant.now())
	println("\nDone after $duration")

	api.shutdown()
}
