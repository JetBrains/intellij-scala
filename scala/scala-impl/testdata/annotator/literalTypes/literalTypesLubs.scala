object Test {
  val x1: Int    = if (true) 1 else 2
  val x2: Long   = if (true) 1 else 2
  val x3: Long   = if (true) 1 else 2L
  val x4: AnyVal = if (true) 1 else 2
  val x5: AnyVal = if (true) 1.0 else 2L
  val x6: AnyVal = if (true) 1.0 else true

  val x7: String = if (true) "1" else "2"
  val x8: AnyRef = if (true) "1" else 'foo
  val x9: Any    = if (true) "1" else 1
}