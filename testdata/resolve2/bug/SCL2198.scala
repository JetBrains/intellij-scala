def f(x: {val name: String; val age: Int}) = {
	import x._
	println(/* resolved: true */name + " is " + /* resolved: true */age + " years old")
}