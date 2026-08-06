def f[X: T] = {}

trait T[A]

println(/* offset: 4, applicable: false */ f[Int])