object Test {
  class C
  class A {
    val foo: C = new C
    def foo(x: Int): String = ""
  }

  val a = new A
  implicit def c2fun = (c: C) => (x: String) => ""
  a./* resolved: false */foo("")
  a./* line: 5 */foo(3)
}