object Test {
  def foo(x: Int): Int = 1
  def foo(x: String): Int = 3

  /* line: 2 */foo(3 : Byte)
}