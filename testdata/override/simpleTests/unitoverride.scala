implement foo
package test

class Foo extends b {
  <caret>
}
abstract class b {
  def foo(x: b): Unit
}<end>
package test

class Foo extends b {
  def foo(x: b) {}
}

abstract class b {
  def foo(x: b): Unit
}