object Test {
  def foo(x: Unit): Int = 1
  def foo(x: String): Int = 3

  /* resolved: false */foo(3)
}