package tests

object Example:
  def bar(o: AnyRef)

  def foo(o: AnyRef) =
    try
      bar(o)
    catch
      case <caret> =>
