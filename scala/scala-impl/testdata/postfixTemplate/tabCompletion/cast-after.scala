package tests

object Example {
  def foo(o: AnyRef) {
    o.asInstanceOf[<caret>]
  }
}
