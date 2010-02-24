def f[X: T] = {}

trait T[A]

println(/* offset: 4, valid: false */ f[Int])