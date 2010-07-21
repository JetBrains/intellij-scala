def f(x: {val name: String; val age: Int}) = {
	import x._
	println(/* line: 1 */name + " is " + /* line: 1 */age + " years old")
}