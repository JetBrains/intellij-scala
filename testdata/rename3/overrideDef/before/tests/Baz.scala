package tests

class Baz {
  def baz/*caret*/() = 0
}

object Baz2 extends Baz {
  override val baz/*caret*/ = 3
}

object Baz3 extends {
  override var /*caret*/baz = 1
} with Baz {}

class Baz4 extends Baz {
  override def baz/*caret*/() = 1
}

object Test {
  Baz2.baz/*caret*/
  Baz3.baz/*caret*/
  Baz3.baz/*caret*/_=(3)
}