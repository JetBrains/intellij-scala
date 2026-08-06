object Test extends Application {
  class C
  class A {
    val foo: C = new C
    def foo(x: Int, y: Int): String = ""
  }
  class B {
    def foo(x: String) = print("test")
  }

  val a = new A
  implicit def c2fun = (c: C) => (x: String) => ""
  implicit def a2b: A => B = p => new B
  a./* line: 8 */foo("")
}