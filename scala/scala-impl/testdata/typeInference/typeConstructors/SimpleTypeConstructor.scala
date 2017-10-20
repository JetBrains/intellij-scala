type A[T] = {
  def foo(x: T): T
}
def w[T]: A[T] = new {def foo(x: T): T = x}
/*start*/w[Int].foo(23)/*end*/
//Int