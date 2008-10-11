override foo
package test

class A {
  var foo: A = new A
}
class VarOverride extends A {
  val t = foo()
  <caret>
  def y(): Int = 3
}<end>
package test

class A {
  var foo: A = new A
}
class VarOverride extends A {
  val t = foo()
  override var foo: A = _

  def y(): Int = 3
}