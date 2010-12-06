override foo
class A {
  def foo(x_ : Int) = 1
}

class B extends A {
  <caret>
}<end>
class A {
  def foo(x_ : Int) = 1
}

class B extends A {
  override def foo(x_ : Int) = null
}