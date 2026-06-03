package tests

object Baz {
  def baz(i/*caret*/: Int) = {
    1 match {
      case `i/*caret*/` => i/*caret*/
      case _ => 0
    }
  }

  baz(i/*caret*/ = 2)
}
