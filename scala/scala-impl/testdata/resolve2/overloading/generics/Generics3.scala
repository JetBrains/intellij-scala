object Test extends Application {
  class A {
    def foo[T <: String](x: Int, y: T) = print(1)
    def foo[T](x: T, y: Int) = print(2)
  }

  val a = new A

  a./* line: 4 */foo(1, 2)
}