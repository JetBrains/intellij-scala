class C1 {
  val v = {}
}

class C2 extends C1 {
  override val v = {}

  println(/* line: 6 */ v)
}
