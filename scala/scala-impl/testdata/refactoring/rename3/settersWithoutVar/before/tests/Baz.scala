package tests

class Baz {
  private[this] var inner = 0

  def ba/*caret*/r_=(i: Int) {
    inner = i
  }

  def bar = inner
}

object Bar extends Baz {
  override var bar = 2
}

object Bar2 extends Baz {
  override def bar/*caret*/_=(i: Int): Unit = super.ba/*caret*/r_=(i)

  override def bar: Int = super.bar
}