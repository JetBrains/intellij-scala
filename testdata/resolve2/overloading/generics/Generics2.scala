object Test extends Application {
  class A {
    def foo[T](x: Int, y: T) = print(1)
    def foo[T](x: T, y: Int) = print(2)
  }

  val a = new A

  a./* resolved: false */foo(1, 2)
  a./* line: 3 */foo(1, false)
}