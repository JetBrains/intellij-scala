class TestClass {
  def foo(arg: String) = foo.foo.foo.foo
  <caret>
}
-----
class TestClass {
  def foo(arg: String) = foo.foo.foo.foo
    .
}