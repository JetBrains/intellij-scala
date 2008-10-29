implement foo
abstract class A {
  def foo[T](x: T): T
}
class SimpleTypeParam extends A {
  <caret>
}<end>
abstract class A {
    def foo[T](x: T): T
}
class SimpleTypeParam extends A {
    def foo[T](x: T): T = null
}