object Test {
  class C
  class A {
    val foo: C = new C
    def foo(x: Int): String = ""
  }

  val a = new A

  a./* line: 5 */foo(3)
}