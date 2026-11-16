package tests

object Use {
  val b: /*caret*/Bar = /*caret*/Bar(1)
  val v: Int = Bar(2).value
}
