package nannextract.model

import java.io.File

interface BlogPostPersister
{
	fun persist(post:BlogPost)
}

// Super fast implementation
class FileBasedBlogPersister(dir:String) : BlogPostPersister
{
	val directory = dir

	init{
		println("Making dir: .\\$dir")
		File(".\\$dir").mkdir()
	}

	override fun persist(post:BlogPost){
		val file = File(".\\$directory\\${post.author.name}.${post.meta.title}.html")
		file.printWriter().use { out ->
			out.print(post.content)
		}
	}

}