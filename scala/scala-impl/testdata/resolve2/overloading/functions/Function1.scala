object Test extends Application {
  def foo(x: Int => Int) = print(1)
  def foo(x: String) = print(2)

  /* line: 2 */foo(p => p)
}