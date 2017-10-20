object Test {
  def foo(x: Byte): Int = 1
  def foo(x: String): Int = 2

  /* resolved: false */foo(3 : Int)
}