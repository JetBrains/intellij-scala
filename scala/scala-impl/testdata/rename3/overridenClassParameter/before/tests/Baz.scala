package tests

class Baz(val baz/*caret*/: Int = 0) {}

object Baz2 extends Baz(baz/*caret*/ = 2) {
  override val baz/*caret*/ = 3
}

object Baz3 extends {
  override val baz/*caret*/ = 1
} with Baz

class Baz4(override val baz/*caret*/: Int = 1) extends Baz

object Test {
  Baz2.baz/*caret*/
  Baz3.baz/*caret*/
}