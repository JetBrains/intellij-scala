object Test {
  def foo(x: String): Int = 1
  def foo(x: Int): Int = 2

  implicit def str2int(x: String): Int = x.length

  /* line: 2 */foo("")
}