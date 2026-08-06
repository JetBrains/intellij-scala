object A {
  class A {
    def foo(x: Int) = 1
  }
  class B {
    def foo(x: C) = 2
  }
  class C
  implicit def foo(a: A): B = new B
  implicit def too(a: A): C = new C

  val a: A = new A

  a./* line: 3, applicable: false */foo(new A)
}