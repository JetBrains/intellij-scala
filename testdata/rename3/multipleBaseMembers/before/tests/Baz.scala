package tests

class Baz {
  def baz() = 0
}

trait Bazzz {
  def baz()
}

object Baz2 extends Baz {
  override val baz = 3
}

object Baz3 extends {override var /*caret*/baz = 1} with Baz with Bazzz {}

object Test {
  Baz2.baz
  Baz3.baz/*caret*/
  Baz3.baz_=/*caret*/(3)
}