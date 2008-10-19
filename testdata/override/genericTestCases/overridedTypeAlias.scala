implement y
abstract class A {
  type K = A
  val y: K
}

class OverridedTypeAlias extends A {
  override type K = B

  <caret>
}<end>
abstract class A {
  type K = A
  val y: K
}

class OverridedTypeAlias extends A {
  override type K = B


  val y: K = _
}