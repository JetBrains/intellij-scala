package tests

object Example {
  def foo(o: AnyRef) {
    do {
      <caret>
    } while (o != null)
  }
}

