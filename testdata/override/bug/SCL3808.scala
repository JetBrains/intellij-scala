override foo
trait TC[_]

class A {
  def foo[M[X], N[X[_]]: TC]: String = ""
}

object B extends A {
  <caret>
}<end>
trait TC[_]

class A {
  def foo[M[X], N[X[_]] : TC]: String = ""
}

object B extends A {
  override def foo[M[X], N[X[_]] : TC]: String = ""
}