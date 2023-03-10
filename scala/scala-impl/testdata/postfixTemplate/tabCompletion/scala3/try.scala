package tests

object Example:
  def bar(o: AnyRef)

  def foo(o: AnyRef) =
    bar(o)<caret>
