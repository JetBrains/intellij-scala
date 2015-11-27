package tests

trait Baz {
  val NameAfterRename = 0

  def foo() = {
    NameAfterRename match {
      case NameAfterRename =>
      case _ =>
    }
  }
}

class BazClass extends Baz {
  override var NameAfterRename = 1
}

object BazInst extends Baz {
  override def NameAfterRename = 1
}

object Test {
  def foo(i: Int = BazInst.NameAfterRename) = {
    BazInst.NameAfterRename.toString
    (new BazClass).NameAfterRename_=(3)
  }
}
