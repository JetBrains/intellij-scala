override foo
class A {
  type F = (Int => String)
  def foo(f: F): Any = null
}

object B extends A {
  <caret>
}<end>
class A {
  type F = (Int => String)

  def foo(f: F): Any = null
}

object B extends A {
  override def foo(f: B.F): Any = null
}