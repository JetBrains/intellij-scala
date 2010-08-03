object Test {
  def a(x: Throwable) = 1
  def a(x: AnyRef) = 1

  def b(x: AnyRef) = 2
  def b(x: Throwable) = 2

  val x: Throwable = null

  /* line: 2 */a(x)
  /* line: 6 */b(x)
}