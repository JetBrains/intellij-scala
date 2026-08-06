object SCL8317 {
  def foo(x: Boolean): Int = 1

  def foo(s: String): String = null

  implicit def u(u: Unit): Boolean = false

  /*start*/foo()/*end*/
}
//Int