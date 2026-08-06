package tests

class Baz {
  val baz = 0
}

object Baz2 extends Baz {
  override val baz/*caret*/ = 3
}

object Baz3 extends {
  override val /*caret*/baz = 1
} with Baz

object Test {
  Baz2.baz/*caret*/
  Baz3.baz/*caret*/
}