object SCL7618 {
  class A[T]
  class B extends A[Int]
  def foo[T, M[_] <: A[_]](x: M[T]): M[T] = x

  /*start*/foo(new B)/*end*/
}
//SCL7618.A[Int]