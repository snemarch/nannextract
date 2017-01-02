package nannextract.model

import java.time.LocalDate

data class Contact(val userId:Int, val userName:String, val title:String, val lastLogin: LocalDate)
