object Test {
  class A {
    val foo: Boolean => String = p => ""
    def foo(x: Int): String = ""
  }

  val a = new A

  a./* resolved: false */foo("")
}