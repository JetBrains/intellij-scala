object SCL7388 {
  class A[T]

  def foo[T](a: A[T]): A[T] = a

  def foo(x: Int): Int = 123

  val x: A[String] = foo(/*start*/new A/*end*/)
}
//SCL7388.A[String]