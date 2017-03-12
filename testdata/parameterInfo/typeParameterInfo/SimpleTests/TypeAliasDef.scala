trait X[U] {
  type Pair[a <: U] = (a, a)
}

val x: X[String] = null
("", ""): x.Pair[String<caret>]
// a <: String