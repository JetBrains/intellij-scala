package tests

class Baz {
  def NameAfterRename() = 0
}

trait Bazzz {
  def NameAfterRename()
}

object Baz2 extends Baz {
  override val NameAfterRename = 3
}

object Baz3 extends {override var NameAfterRename = 1} with Baz with Bazzz {}

object Test {
  Baz2.NameAfterRename
  Baz3.NameAfterRename
  Baz3.NameAfterRename_=(3)
}