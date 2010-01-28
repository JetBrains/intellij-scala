override foo
class A[T] {
  def foo: T = new T
}

class ClassTypeParam extends A[Int] {
  <caret>
}<end>
class A[T] {
  def foo: T = new T
}

class ClassTypeParam extends A[Int] {
  override def foo: Int = 0
}