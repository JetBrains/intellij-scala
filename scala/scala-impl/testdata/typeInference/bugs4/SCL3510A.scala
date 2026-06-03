trait M[X]
trait N[X]

class Foo[A](a: N[A]) {
  def this(as: M[A]) = this (null)
}

val mi: M[Int] = null
/*start*/new Foo(mi)/*end*/
// Foo[Int]