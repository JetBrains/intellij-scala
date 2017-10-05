def f(p: Int*) = {}

val args1: Array[String] = Array("a", "b")
println(/* offset: 4, applicable: false */  f(args1: _*))