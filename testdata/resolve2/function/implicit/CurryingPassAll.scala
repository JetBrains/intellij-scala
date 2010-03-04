def f(a: Int)(implicit b: Int) = {}

println(/* offset: 4 */ f(1)(2))
