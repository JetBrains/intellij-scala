trait I {
  def f = {}
}

trait T extends I {
  abstract override def f = {}
}

class C extends T {
  override def f = {}

  println(/* line: 10 */f)
  println(this./* line: 10 */f)
  println(super./* line: 6 */f)
}