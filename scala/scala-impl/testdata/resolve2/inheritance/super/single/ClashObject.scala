class O1 {
  def f {}
}

object O2 extends O1 {
  override def f {}

  println(/* line: 6 */f)
  println(super./* line: 2 */f)
}