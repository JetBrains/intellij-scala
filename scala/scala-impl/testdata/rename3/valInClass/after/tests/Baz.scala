package tests

class Baz {
  val NameAfterRename = 0

  def foo() = {
    NameAfterRename match {
      case NameAfterRename =>
      case _ =>
    }
  }
}

object BazInst extends Baz {
  override def NameAfterRename = 1
}

object BazInst2 extends Baz {
  override var NameAfterRename = 2
}

object Test {
  def foo(i: Int = BazInst.NameAfterRename) = {
    BazInst.NameAfterRename.toString
    BazInst2.NameAfterRename = 1
    (new Baz).NameAfterRename.toString
  }
}
