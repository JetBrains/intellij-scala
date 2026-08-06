object SCL4513 {
  trait A
  trait B
  class C
  def foo[T <: A](x: T) : T = x
  def foo[T](x: T)(implicit impl: Int => String): C = new C

  implicit val x = (x: Int) => ""

  /*start*/foo(new B {})/*end*/
}
//SCL4513.C