trait T1 {
  def f = {}
}

trait T2 {
  self: T1 =>

  println(/* line: 2 */f)
  println(this./* line: 2 */f)
  println(super./* resolved: false */f)
}