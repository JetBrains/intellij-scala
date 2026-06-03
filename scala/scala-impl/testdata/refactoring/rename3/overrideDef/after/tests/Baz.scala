package tests

class Baz {
  def NameAfterRename() = 0
}

object Baz2 extends Baz {
  override val NameAfterRename = 3
}

object Baz3 extends {
  override var NameAfterRename = 1
} with Baz {}

class Baz4 extends Baz {
  override def NameAfterRename() = 1
}

object Test {
  def foo(): Unit = {
    class Baz5 extends Baz {
      override def NameAfterRename(): Int = 5
    }
  }

  Baz2.NameAfterRename
  Baz3.NameAfterRename
  Baz3.NameAfterRename_=(3)
}