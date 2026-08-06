class C1 {
  def f {}
}

class C2 extends C1 {
  override def f {}

  println(/* line: 6 */ f)
}
