package tests

object Example {
  def foo(o: AnyRef) {
    throw new RuntimeException()<caret>
  }
}
