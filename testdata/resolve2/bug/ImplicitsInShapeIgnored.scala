object Test {
  class A {
    def foo(x: String, y: String) = 2
  }

  class B {
    def foo(x: Int => Int, z: String) = 3
  }

  implicit def a2b(x: A): B = new B

  val a = new A

  print(a./* line: 7 */foo((p: Int) => p, ""))
  print(a./* resolved: false */foo(p => p, ""))
}