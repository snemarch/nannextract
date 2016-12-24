import nannextract.util.DateUtil
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.function.Supplier

class DateTests {
	private val testDateSupplier = Supplier{ LocalDate.of(2016, 12, 24) }

	@Test
	fun testToday() {
		val expected = LocalDate.of(2016, 12, 24)
		assertEquals(expected, DateUtil.parseNaturalDate(testDateSupplier, "I dag"))
	}

	@Test
	fun testYesterday() {
		val expected = LocalDate.of(2016, 12, 23)
		assertEquals(expected, DateUtil.parseNaturalDate(testDateSupplier, "I går"))
	}

	@Test
	fun testDayBeforeYesteryday() {
		val expected = LocalDate.of(2016, 12, 22)
		assertEquals(expected, DateUtil.parseNaturalDate(testDateSupplier, "I forgårs"))
	}

	@Test
	fun testMonthAndDay() {
		val expected = LocalDate.of(2016, 4, 3)
		assertEquals(expected, DateUtil.parseNaturalDate(testDateSupplier, "3/4"))
	}

	@Test
	fun testFullySpecified() {
		val expected = LocalDate.of(2015, 12, 22)
		assertEquals(expected, DateUtil.parseNaturalDate(testDateSupplier, "22/12-2015"))
	}
}
