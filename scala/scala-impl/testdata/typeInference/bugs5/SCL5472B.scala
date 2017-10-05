object SCL5472B {
  class A
  class B extends A
  def foo(a: A): a.type = a
  implicit def t2a(x: (Int, Int)): B = new B

  /*start*/foo(1, 2)/*end*/
}
//SCL5472B.B