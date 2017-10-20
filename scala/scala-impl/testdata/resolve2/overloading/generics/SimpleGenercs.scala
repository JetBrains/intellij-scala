object Test extends Application {
  class A {
    def foo[T](x: T) = print(1)
    def foo(x: Boolean) = print(2)
  }

  val a = new A

  a./* line: 3 */foo[Boolean](false)
  a./* line: 4 */foo(false)
}