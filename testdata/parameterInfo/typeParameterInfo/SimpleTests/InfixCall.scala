trait X[U] {
  def foo[a <: U, b](s: String) = 0
}

val x: X[String] = null
x foo[Int, <caret>] ""
// a <: String, b