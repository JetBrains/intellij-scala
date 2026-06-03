object ProjectionGeneric {
  class Matchers {
    class Clazz[T](val x: T)
    class Inner {
      def foo[T](x: Clazz[T]): T = x.x
    }

    class Other {
      def foo(x: Boolean) = 2
    }

    implicit def str2inner(x: String): Inner = new Inner
    implicit def str2other(x: String): Other = new Other
  }

  class Child extends Matchers {
    val clazz = new Clazz[Int](2)
    /*start*/"" foo clazz/*end*/
  }
}
//Int