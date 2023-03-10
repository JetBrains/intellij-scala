package tests

object Example:
  def foo(o: AnyRef) =
    if !(o == null) then {<caret>}
