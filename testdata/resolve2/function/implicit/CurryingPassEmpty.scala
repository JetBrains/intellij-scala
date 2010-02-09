def f(a: Int)(implicit b: Int) = {}

implicit val v: Int = 1

println(/* applicable: false */ f)
