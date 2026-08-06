//> expected.error cannot.inline.notsimple.typealias
class A {
  type S
  type /*caret*/Q = S
  val a = new A {
    override type S = Int
  }
  val i: a.Q = 2
}