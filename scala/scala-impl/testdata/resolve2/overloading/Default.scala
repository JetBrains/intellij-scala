object Test {
  def foo(x: Int, y: String = ""): Int = 1
  def foo(x: String, y: String): Int = 2

  /* line: 2 */foo(1)
  /* line: 3 */foo("", "")
}