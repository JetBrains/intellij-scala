object SCL4150 {
  class U[T]
  class JK extends U[Int]
  class B[T] extends JK
  trait Z[T]
  trait C[T] extends Z[Int]
  class A[T, K] extends B[T] with C[T]
  def foo[T[_], H](t: T[H]): T[H] = ???

  /*start*/foo(new A[Int, String])/*end*/
}
/*
SCL4150.B[Int]
[Scala_2_13]SCL4150.A[Int, String]
*/