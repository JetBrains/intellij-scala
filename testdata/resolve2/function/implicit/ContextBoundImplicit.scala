trait T[X]

def f[A](implicit t: T[A]) = {}

def g[X: T] {
  println(/* offset: 13 */ f[X])
}
