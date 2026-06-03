def f(implicit i: Int) = {}

println(/* offset: 4 */ f)

implicit val v: Int = 1