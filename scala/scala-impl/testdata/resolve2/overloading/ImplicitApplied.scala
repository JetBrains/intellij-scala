object Test {
  def foo(x: Boolean): Int = 1
  def foo(x: Int): Int = 2

  implicit def str2int(x: String): Int = x.length

  /* line: 3 */foo("")
}