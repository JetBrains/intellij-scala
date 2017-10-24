trait T1 {
  def a {}
}

trait T2 extends T1 {
  def b {}
}

trait T3 extends T2 {
  override def a {}
  override def b {}

  println(/* line: 10 */a)
  println(/* line: 11 */b)

  println(super./* line: 2 */a)
  println(super./* line: 6 */b)
}