object SCL7901 {
  class I
  class J extends I
  class A {
    def foo(j: J) = 1
  }

  class B extends A {
    def foo(i: I)(s: String) = 2
  }

  val b = new B
  val i: Int = /*start*/b.foo(new J)/*end*/
}
//Int