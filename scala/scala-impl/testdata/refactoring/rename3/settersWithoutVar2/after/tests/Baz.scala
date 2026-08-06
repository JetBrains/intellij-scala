package tests

class Baz {
  private[this] var inner = 0

  def NameAfterRename_=(i: Int) {
    inner = i
  }

  def NameAfterRename = inner
}

object Bar extends Baz {
  override var NameAfterRename = 2
}

object Bar2 extends Baz {
  override def NameAfterRename_=(i: Int): Unit = super.NameAfterRename_=(i)

  override def NameAfterRename: Int = super.NameAfterRename
}