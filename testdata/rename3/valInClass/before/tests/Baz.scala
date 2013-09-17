package tests

class Baz {
  val bar/*caret*/ = 0
  def foo() = {
    ba/*caret*/r match {
      case `bar`/*caret*/ =>
      case _ =>
    }
  }
}

object Bar extends Baz {
  override val bar = 1
}

object Test {
  def foo(i: Int = Bar./*caret*/bar) = {
    Bar.ba/*caret*/r.toString
  }
}