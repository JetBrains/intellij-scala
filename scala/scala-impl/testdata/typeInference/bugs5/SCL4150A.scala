object SCL4150 {
  class U[T]
  class JK extends U[Int]
  class B extends JK
  trait Z[T]
  trait C[T] extends Z[Int]
  class A[T, K] extends B with C[T]
  def foo[T[_], H](t: T[H]): T[H] = ???

  /*start*/foo(new A[Int, String])/*end*/
}
/*
SCL4150.C[Int]
[Scala_2_13]SCL4150.A[Int, String]
*/