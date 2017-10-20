def f(implicit i: Int) = {}

val v: Int = 1

println(/* offset: 4, applicable: false */ f)