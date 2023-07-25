trait X[U] {
  type Pair[a <: U] = (a, a)
}

object Example {
  val x: X[String] = null
  ("", ""): x.Pair[String <caret>]
}
//TEXT: a <: String, STRIKEOUT: false