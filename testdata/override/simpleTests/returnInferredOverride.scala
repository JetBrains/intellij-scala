override foo
package test

class Foo extends b {
  <caret>
}
abstract class b {
  def foo(x: b) = 1
}<end>
package test

class Foo extends b {
  override def foo(x: b): Int = 0
}

abstract class b {
  def foo(x: b) = 1
}