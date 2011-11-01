object Test extends Application {
  class X
  class Y extends X
  class C {
    def apply(x: X, y: Y) = 1
    def apply(x: Y, y: X) = 2
  }
  class A {
    val foo: C = new C
    def foo(x: X, y: X) = 3
  }

  val a = new A
  val z = a./* line: 10 */foo(new X, new X)
  a./* line: 9 */foo(new X, new Y)
  print(z)
}