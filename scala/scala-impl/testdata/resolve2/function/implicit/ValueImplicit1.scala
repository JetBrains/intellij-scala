def f(implicit i: Int) = {}

implicit val v: Int = 1

println(/* offset: 4 */ f)