object Test {
  class C
  class A {
    val foo: C = new C
  }
  class B {
    def foo(x: Boolean) = ""
  }

  val a = new A
  implicit def c2fun = (c: C) => (x: String) => ""
  implicit def a2b: A => B = p => new B
  a./* file: Function1, name: apply */foo("")
}