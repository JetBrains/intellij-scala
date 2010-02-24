def f(a: Int)(implicit b: Int) = {}

implicit val v: Int = 1

println(/* offset: 4, valid: false */ f)
