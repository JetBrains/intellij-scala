def f(p: Int*) = {}

val args: Array[String] = Array("a", "b")
println(/* offset: 4, applicable: false */  f(args: _*))