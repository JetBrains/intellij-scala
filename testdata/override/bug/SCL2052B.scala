override foo
class A {
  type ID[X] = X
  val foo: ID[Int] = null
}

class B extends A {
  <caret>
}<end>
class A {
  type ID[X] = X
  val foo: ID[Int] = null
}

class B extends A {
  override val foo: B#ID[Int] = _
}