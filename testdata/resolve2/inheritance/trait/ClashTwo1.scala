trait T1 {
  def f = {}
}

trait T2 {
  def f = {}
}

class C extends T1 with T2 {
  override def f = {}

  println(/* line: 10 */f)
  println(this./* line: 10 */f)
  println(super./* line: 6 */f)
}

