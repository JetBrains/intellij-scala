def f(implicit i: Int) = {}

implicit val v: Int = 1

println(/* applicable: false */ f())