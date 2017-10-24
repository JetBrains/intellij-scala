object SCL6786 {
  import language.dynamics
  class A extends Dynamic {
    def applyDynamic(s: String)(i: Int): A = new A
  }

  class B extends Dynamic {
    def applyDynamic[T](s: String)(t: T): T = t
  }

  class C extends Dynamic {
    def applyDynamic(s: String)(a: Int, b: Int) = a + b
  }

  val a = new A
  val b = new B
  val c = new C

  /*start*/(a.foo(1)(2)(3), b.foo("text"), c(1) = 2)/*end*/
}
//(SCL6786.A, String, Int)