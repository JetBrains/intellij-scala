trait T1 {
  def f = {}
}

trait T2 { self: T1 => 
  abstract override def f = {}

  println(/* line: 6 */f)
  println(this./* line: 6 */f)
  println(super./* resolved: false */f)
}