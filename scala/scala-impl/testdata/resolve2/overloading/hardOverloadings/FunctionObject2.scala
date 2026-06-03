object Test {
  class A {
    object foo {
      def apply(x: Int) = 23
      def apply(x: Boolean) = 77
    }

    def foo(x: String) = 1
  }

  val a = new A

  a./* line: 5, name: apply */foo(false)
}