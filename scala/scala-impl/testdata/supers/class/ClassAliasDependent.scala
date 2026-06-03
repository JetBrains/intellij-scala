object Testo {
  trait A {
    type Z <: U
    class U

    class O {
      def foo(x: Z) = 1
    }
  }

  class H extends A {
    class Z extends U

    class I extends O {
      override def <caret>foo(x: Z) = 2
    }
  }
}