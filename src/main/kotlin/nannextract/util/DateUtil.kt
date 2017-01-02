package nannextract.util

import java.time.LocalDate
import java.util.function.Supplier
import java.util.regex.Pattern

class DateUtil {
	companion object {
		private val fullDateMatcher = Pattern.compile("(\\d+)/(\\d+)-(\\d+)")!!
		private val currentMonthMatcher = Pattern.compile("(\\d+)/(\\d+)")!!

		fun parseNaturalDate(currentDaySupplier: Supplier<LocalDate>, dateString: String): LocalDate {
			val today = currentDaySupplier.get()

			val monthMatcher = currentMonthMatcher.matcher(dateString)
			val fullMatcher = fullDateMatcher.matcher(dateString)

			when {
				dateString.toLowerCase().startsWith("i dag") -> return today
				dateString.toLowerCase().startsWith("i går") -> return today.minusDays(1)
				dateString.toLowerCase().startsWith("i forgårs") -> return today.minusDays(2)
				fullMatcher.find() -> {
					val day = fullMatcher.group(1).toInt()
					val month = fullMatcher.group(2).toInt()
					val year = fullMatcher.group(3).toInt()
					return LocalDate.of(year, month, day)
				}
				monthMatcher.find() -> {
					val day = monthMatcher.group(1).toInt()
					val month = monthMatcher.group(2).toInt()
					return LocalDate.of(today.year, month, day )
				}
			}

			return today
		}
	}
}
