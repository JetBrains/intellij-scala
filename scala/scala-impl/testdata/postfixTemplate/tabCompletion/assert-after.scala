package tests

object Example {
  def foo(o: AnyRef) {
    assert(o != null)<caret>
  }
}
