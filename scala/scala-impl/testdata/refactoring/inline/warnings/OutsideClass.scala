//> expected.error cannot.inline.used.outside.class
object X {
  val /*caret*/b = 1
}

object Y {
  val b = X.b
}