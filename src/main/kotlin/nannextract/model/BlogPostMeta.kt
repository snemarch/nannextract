package nannextract.model

import java.time.LocalDate

data class BlogPostMeta(val id:Int, val title:String, val date: LocalDate, val numViews:Int)
