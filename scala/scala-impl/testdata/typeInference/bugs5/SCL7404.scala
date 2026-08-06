object SCL7404 {
  class A
  class B extends A
  class Z[T]
  implicit object KA extends Z[A]
  implicit object KB extends Z[B]

  implicit def foo[T](t: (Int, T))(implicit z: Z[T]) = 123

  def goo(x: Int) = 123
  def goo(s: String) = "text"

  /*start*/goo((1, new B))/*end*/
}
//Int