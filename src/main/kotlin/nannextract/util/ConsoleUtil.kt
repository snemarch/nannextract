package nannextract.util

// Produces a progressbar string based on a current value
// and a total value, with optional length and optional caller
// supplied symbols to use
// If the below conditions aren't upheld behavior is undefined
// Both current and total have to be larger than 0
// current has to be smaller than total
// if supplied, length has to be larger than 0
// if supplied, suppliedSymbol array can't be empty
fun generateProgressBar(current:Int, total:Int, barLength:Int = 20, symbols: CharArray = charArrayOf(' ', '.', 'o','0', '@')) : String
{
	val percent = current.toDouble() / total

	val chars = CharArray(barLength, {i -> symbols.first()})
	val amount = barLength * percent
	val amountWhole = Math.floor(amount).toInt()
	val amountRest = amount - amountWhole

	chars.fill(symbols.last(), 0, amountWhole)
	if(amountWhole != barLength) {
		val index = Math.round(amountRest * (symbols.size - 1)).toInt()
		chars[amountWhole] = symbols[index]
	}
	return String(chars)
}