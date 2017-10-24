object Test extends Application {
  def foo(x: Int => Int)(y: Int) = print(1)
  def foo(x: String => String => String) = print(2)

  /* line: 3 */foo(p => p => p)
}