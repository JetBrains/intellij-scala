object Test {
  class A {
    object foo {
      def apply(x: Int) = 23
    }

    def foo(x: String) = 1
  }

  val a = new A

  a./* line: 4, name: apply */foo(4)
  a./* line: 7 */foo("")
}