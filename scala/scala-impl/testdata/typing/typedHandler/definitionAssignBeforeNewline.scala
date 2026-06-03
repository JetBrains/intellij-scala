class TestClass {
  def foo(arg: String) =
  <caret>
}
-----
class TestClass {
  def foo(arg: String) =
    a
}