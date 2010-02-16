trait T1 {
  def f = {}
}

trait T2 extends T1 {
  abstract override def f = {}

  println(/* line: 6 */f)
  println(this./* line: 6 */f)
  println(super./* line: 2 */f)
}