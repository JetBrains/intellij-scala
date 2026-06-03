def f[X: T] = {}

trait T[A]
implicit val TInt = new T[Int] {}

println(/* offset: 4 */ f[Int])