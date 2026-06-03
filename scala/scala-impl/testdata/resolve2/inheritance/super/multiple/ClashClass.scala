class C1 {
  def a {}
}

class C2 extends C1 {
  def b {}
}

class C3 extends C2 {
  override def a {}
  override def b {}

  println(/* line: 10 */a)
  println(/* line: 11 */b)

  println(super./* line: 2 */a)
  println(super./* line: 6 */b)
}