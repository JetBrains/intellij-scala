implement foo
package test

trait Foo {
  def foo(a: Any*): Any
}

trait Sub extends Foo {
  <caret>
}<end>
package test

trait Foo {
  def foo(a: Any*): Any
}

trait Sub extends Foo {
  def foo(a: Any*): Any = null
}