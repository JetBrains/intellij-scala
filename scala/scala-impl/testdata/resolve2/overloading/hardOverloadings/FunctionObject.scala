object Test {
  class A {
    object foo {

    }

    def foo(x: String) = 1
  }

  val a = new A

  a./* line: 7 */foo("")
}