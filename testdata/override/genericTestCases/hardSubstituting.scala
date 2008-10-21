override foo
class A[T] {
  def foo(x: (T) => T, y: (T, Int) => T): Double = 1.0
}

class Substituting extends A[Float] {
  <caret>
}<end>
class A[T] {
  def foo(x: (T) => T, y: (T, Int) => T): Double = 1.0
}

class Substituting extends A[Float] {
  override def foo(x: (Float) => Float, y: (Float, Int) => Float): Double = null
}