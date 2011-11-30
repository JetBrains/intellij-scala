class C(private val name: String)

class User {
	val c = new C("some")
	println(c./* accessible: false */name)
}