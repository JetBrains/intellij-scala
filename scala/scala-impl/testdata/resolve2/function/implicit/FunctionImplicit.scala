def f(implicit i: Int) = {}

implicit def v: Int = 1

println(/* offset: 4 */ f)