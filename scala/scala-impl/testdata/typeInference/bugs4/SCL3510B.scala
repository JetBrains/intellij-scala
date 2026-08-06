trait M[X]
trait N[X]

class Foo[A](a: N[A]) {
  def this(as: M[A]) = this (null)
}

val nb: N[Boolean] = null
/*start*/new Foo(nb)/*end*/
// Foo[Boolean]