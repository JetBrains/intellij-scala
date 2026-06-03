object Test extends Application {
  class A {
    def foo[T <: Boolean](x: T) = print(1)
    def foo(x: Int, e: Int = 1) = print(2)
  }

  val a = new A

  a./* line: 4 */foo(2)
  a./* line: 3 */foo(false)
}