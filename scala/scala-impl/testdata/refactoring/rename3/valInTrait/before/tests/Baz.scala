package tests

trait Baz {
  val bar/*caret*/ = 0

  def foo() = {
    bar/*caret*/ match {
      case `bar`/*caret*/ =>
      case _ =>
    }
  }
}

class BazClass extends Baz {
  override var bar = 1
}

object BazInst extends Baz {
  override def bar = 1
}

object Test {
  def foo(i: Int = BazInst.bar) = {
    BazInst.bar.toString
    (new BazClass)./*caret*/bar_=(3)
  }
}
