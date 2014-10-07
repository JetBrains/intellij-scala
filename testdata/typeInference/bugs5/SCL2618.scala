object SCL2618 {
  class A[T]
  type F[T] = A[T]
  class Z[CC[_]]

  def foo(x: Int): Int = 2
  def foo(x: Z[F]): String = "text"

  /*start*/foo(new Z[A])/*end*/
}
//String