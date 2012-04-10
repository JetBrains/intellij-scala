override foo
package test

class Foo extends b {
  <caret>
}
abstract class b {
  def foo(x: b) = ()
}<end>
package test

class Foo extends b {
  override def foo(x: b) {}
}

abstract class b {
  def foo(x: b) = ()
}