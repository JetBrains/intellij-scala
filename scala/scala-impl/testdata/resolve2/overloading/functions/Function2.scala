object Test extends Application {
  def foo(x: Int => Int) = print(1)
  def foo(x: String => String) = print(2)

  /* resolved: false */foo(p => p)
}