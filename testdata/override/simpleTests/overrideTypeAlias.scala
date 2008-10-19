override K
class A {
  type K = Int
}
class TypeAlias extends A {
  val t = foo()
  <caret>
  def y(): Int = 3
}<end>
class A {
  type K = Int
}
class TypeAlias extends A {
  val t = foo()

  override type K = Int

  def y(): Int = 3
}