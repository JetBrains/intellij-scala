//> expected.error cannot.inline.stable.reference
val /*caret*/a = 0
a + a
1 match {
  case `a` =>
  case _ =>
}