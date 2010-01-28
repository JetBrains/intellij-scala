override foo
package test

class A {
  def foo(): A = null
}
class FunctionOverride extends A {
  val t = foo()


  <caret>
}<end>
package test

class A {
  def foo(): A = null
}
class FunctionOverride extends A {
  val t = foo()


  override def foo(): A = null
}