package tests

class Baz {
  val bar/*caret*/ = 0

  def foo() = {
    ba/*caret*/r match {
      case `bar/*caret*/` =>
      case _ =>
    }
  }
}

object BazInst extends Baz {
  override def bar = 1
}

object BazInst2 extends Baz {
  override var bar = 2
}

object Test {
  def foo(i: Int = BazInst.bar) = {
    BazInst.bar.toString
    BazInst2.bar = 1
    (new Baz).bar/*caret*/.toString
  }
}
