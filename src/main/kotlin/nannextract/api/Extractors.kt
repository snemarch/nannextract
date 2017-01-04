package nannextract.api

import nannextract.model.Contact
import nannextract.util.DateUtil
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.util.function.Supplier
import java.util.regex.Pattern

class Extractors {
	companion object {
		private val uidMatcher = Pattern.compile(".*?uid=(\\d+).*")

		fun extractUserId(href:String) : Int {
			val matcher = uidMatcher.matcher(href)
			return if(matcher.find()) matcher.group(1).toInt() else -1
		}

		fun extractContacts(dom:Document, currentDateSupplier:Supplier<LocalDate>) : List<Contact> {
			val contactRows = dom.select("form[action='/Friend'] table.pane > tbody > tr:nth-of-type(4) > td > table > tbody > tr:gt(1)")

			return contactRows.map {
				val idAndTitle = it.select("tr > td:eq(0) > a")

				val userId = extractUserId(idAndTitle.attr("href"))
				val title = idAndTitle.attr("title").trim() // gender + age + "online nu"
				val name = idAndTitle.text().trim()
				val lastLogin = DateUtil.parseNaturalDate(currentDateSupplier, it.select("tr > td:eq(3").text().trim())

				Contact(userId = userId, userName = name, title = title, lastLogin = lastLogin)
			}
		}

		/**
		 * Extracts the main content area (as used in profile and presentation)
		 *
		 * @return main html contents with <script> elements removed, null for a nonexisting profile/presentation
		 */
		fun extractMainContent(dom: Document): String? {
			// Empty pages has "Black Market" in header, otherwise it's "${name}s profil" or "${name}s præsentation".
			val emptyCheck = dom.select("div.mainContainer > div.heading > div.pageheader")
			if("Black Market" == emptyCheck.text()) {
				return null
			}

			val content = dom.select("div.mainContainer > div.content")
			content.select("script").remove()
			return content.html()
		}
	}
}
