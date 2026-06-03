object Test {
  class A {
    val foo: String => String = p => p
    def foo(x: String): String = x + "1"
  }

  val a = new A

  a./* resolved: false */foo("")
}