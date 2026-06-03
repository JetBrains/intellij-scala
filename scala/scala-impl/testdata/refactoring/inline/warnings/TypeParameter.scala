//> expected.error cannot.inline.notsimple.typealias
class A[T](t: T) {
  type /*caret*/S = T
  val a = new A(1)
  val i: a.S = 2
}