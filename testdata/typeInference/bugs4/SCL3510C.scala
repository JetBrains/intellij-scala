trait M[X]
trait N[X]

class Foo[A](a: N[A]) {
}

val nb: N[Boolean] = null
/*start*/new Foo(nb)/*end*/
// Foo[Boolean]