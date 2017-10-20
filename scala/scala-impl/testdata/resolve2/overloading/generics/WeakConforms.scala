object Test extends Application {
  class A {
    def foo[T](x: T) = print(1)
    def foo(x: Long) = print(2)
  }

  val a = new A

  a./* line: 4 */foo(2)
}