def f(p: Int*) = {}

val args1: Array[Int] = Array(1, 2)
println(/* offset: 4, applicable: false */  f(args1))