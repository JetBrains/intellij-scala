abstract class A {
  class F
  object F {
    implicit class RichF(f: F) {
      def foo() = 123
    }
  }

  type Z <: F

  def foo(): Z


  /*start*/foo().foo()/*end*/
}
//Int