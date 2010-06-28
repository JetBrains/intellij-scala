object Test {
  def foo(x: String, z: String, y: String = ""): Int = 1
  def foo(x: String, y: String): Int = 2

  /* line: 3 */foo("", "")
}