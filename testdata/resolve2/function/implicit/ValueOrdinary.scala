def f(implicit i: Int) = {}

val v: Int = 1

println(/* applicable: false */ f)