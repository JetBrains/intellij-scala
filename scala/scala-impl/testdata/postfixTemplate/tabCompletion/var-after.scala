package tests

object Example {
  def foo(o: AnyRef) = {
    val i: Int = 2
    i<caret> + 5
  }
}
