def f(p: Int*) = {}

val args: Array[Int] = Array(1, 2)
println(/* offset: 4 */  f(args: _*))