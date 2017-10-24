object Test extends Application {
  class A {
    def foo[T](x: T) = print(1)
    def foo(x: C) = print(2)
  }
  class C
  class D extends C

  val a = new A

  a./* line: 3 */foo[C](new C)
  a./* line: 4 */foo(new D)
}