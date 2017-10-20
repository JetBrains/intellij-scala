object SCL8119B {
  class A
  def foo[T <: A](implicit a: T): T = a

  implicit val a: A = new A
  implicit object B extends A {
    def f = 1
  }

  /*start*/foo.f/*end*/
}
//Int