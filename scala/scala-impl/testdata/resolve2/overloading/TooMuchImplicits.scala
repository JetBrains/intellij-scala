object Test {
  def foo(x: Boolean): Int = 1
  def foo(x: Int): Int = 2

  implicit def str2int(x: String): Int = x.length
  implicit def str2bool(x: String): Boolean = false

  /* resolved: false */foo("")
}