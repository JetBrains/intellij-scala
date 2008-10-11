override foo
package test

class A {
  val foo: A = new A
}
class OverrideValue extends A {
  val t = foo()
  <caret>
}<end>
package test

class A {
  val foo: A = new A
}
class OverrideValue extends A {
  val t = foo()
  override val foo: A = _
}