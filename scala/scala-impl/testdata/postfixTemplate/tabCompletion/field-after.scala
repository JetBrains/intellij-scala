package tests

object Example {
  val i: Int = 2

  def foo(o: AnyRef) = {
    i<caret> + 5
  }
}
