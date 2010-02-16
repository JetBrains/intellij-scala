trait I {
  def f = {}
}

trait T extends I {
  abstract override def f = {}
}

class C extends I with T {
  println(/* line: 6 */f)
  println(this./* line: 6 */f)
  println(super./* line: 6 */f)
}