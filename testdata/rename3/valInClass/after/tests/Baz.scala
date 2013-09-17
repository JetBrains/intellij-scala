package tests

class Baz {
  val bar = 0
  def foo() = {
    bar match {
      case `bar` =>
      case _ =>
    }
  }
}

object Bar extends Baz {
  override val bar = 1
  override def foo() = {

  }
}

object Test {
  def foo(i: Int = Bar.bar) = {
    Bar.bar.toString
  }
}