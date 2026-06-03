package tests

object Example {
  def foo(o: AnyRef) {
    new RuntimeException()<caret>
  }
}
