class A[F](x: F) {
  def map[T](f: F => T): T = f(x)
}

type I[F] = {
  def map[T](f: F => T): T
}
def foo[F](i: I[F]) = i.map(f => f)

/*start*/foo(new A(1))/*end*/
//Int