def f(implicit i: Int) = {}

implicit var v: Int = 1

println(/* offset: 4 */ f)