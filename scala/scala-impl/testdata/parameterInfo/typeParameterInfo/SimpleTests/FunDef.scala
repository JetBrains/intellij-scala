trait X[U] {
  def foo[a <: U, b] = 0
}

object Example {
  val x: X[String] = null
  x.foo[Int, <caret>]
}
//TEXT: a <: String, b, STRIKEOUT: false