trait T[X]

def f[A](implicit t: T[A]) = {}

def g[X: T] {
  println(/* offset: 16 */ f[X])
}
