package nannextract

import nannextract.api.BlackMarketApi
import nannextract.model.User
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

fun main(args : Array<String>) {
	val COOKIEJAR_FILENAME = "cookiejar.json"

	if(args.size < 2) {
		print("Usage: nannextract <username> <password>")
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

}
