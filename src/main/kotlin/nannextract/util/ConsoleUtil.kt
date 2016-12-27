package nannextract.util

// Produces a progressbar string based on a current value
// and a total value, with optional length and optional caller
// supplied symbols to use
// If the below conditions aren't upheld behavior is undefined
// Both current and total have to be larger than 0
// current has to be smaller than total
// if supplied, length has to be larger than 0
// if supplied, suppliedSymbol array can't be empty
fun generateProgressBar(current:Int, total:Int, length:Int = 20, suppliedSymbols: CharArray? = null) : String
{
	val percent = current.toDouble() / total

	val symbols = suppliedSymbols ?: charArrayOf(' ', '.', 'o','0', '@')

	val chars = CharArray(length, {i -> symbols.first()})
	val amount = length * percent
	val amountWhole = Math.floor(amount).toInt()
	val amountRest = amount - amountWhole

	chars.fill(symbols.last(), 0, amountWhole)
	if(amountWhole != length) {
		val index = Math.round(amountRest * (symbols.size - 1)).toInt()
		chars[amountWhole] = symbols[index]
	}
	return String(chars)
}