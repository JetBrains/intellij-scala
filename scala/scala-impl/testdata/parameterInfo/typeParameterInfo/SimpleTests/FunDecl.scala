trait X[U] {
  def foo[a <: U, b]
}

object Example {
  val x: X[String] = null
  x.foo[Int, <caret>]
}
// a <: String, b