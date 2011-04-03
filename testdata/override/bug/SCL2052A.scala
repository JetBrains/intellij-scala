override foo
class A {
  type ID[X] = X
  def foo(in: ID[String]): ID[Int] = null
}

class B extends A {
  <caret>
}<end>
class A {
  type ID[X] = X

  def foo(in: ID[String]): ID[Int] = null
}

class B extends A {
  override def foo(in: B#ID[String]): B#ID[Int] = null
}