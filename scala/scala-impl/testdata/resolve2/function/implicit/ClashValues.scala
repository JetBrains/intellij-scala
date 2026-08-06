def f(implicit i: Int) = {}

implicit val a: Int = 1
implicit val b: Int = 2

println(/* offset: 4, applicable: false */ f)