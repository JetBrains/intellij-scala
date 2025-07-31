package tests

opaque type Bar

object /*caret*/Bar {
  type A = Bar
  val x = /*caret*/Bar
}
