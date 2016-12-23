package nannextract

import nannextract.api.BlackMarketApi

fun main(args : Array<String>) {
	if(args.size < 2) {
		print("Usage: nannextract <username> <password>")
	}

	val api = BlackMarketApi()
	val userName = args[0]
	val password = args[1]

	val success = api.login(userName, password)
	if(success) {
		println("Login successful! - dumping cookies")
		api.dumpCookies()
	} else {
		println("Error logging in")
	}
}
