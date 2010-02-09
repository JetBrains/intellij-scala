def f(implicit a: Int, b: Int) = {}

implicit val v: Int = 1

println(/* offset: 4 */ f)