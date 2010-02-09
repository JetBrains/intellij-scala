def f(a: Int)(implicit b: Int) = {}

implicit val v: Int = 1

println(/* */ f(2))
