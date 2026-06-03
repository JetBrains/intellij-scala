package tests

object Example {
  def foo(o: AnyRef) {
    while (o != null) {
      <caret>
    }
  }
}
