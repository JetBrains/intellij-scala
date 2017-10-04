object Test {
  class C {
    def apply(x: Int): String = ""
  }
  class A {
    val foo: C = new C
    def foo(x: Int): String = ""
  }

  val a = new A

  a./* resolved: false */foo(3)
}