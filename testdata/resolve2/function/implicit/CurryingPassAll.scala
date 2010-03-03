def f(a: Int)(implicit b: Int) = {}

println(/* offset: 4, applicable: false */ f(1, 2))
