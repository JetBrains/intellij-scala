package tests

class Baz {
  val NameAfterRename = 0
}

object Baz2 extends Baz {
  override val NameAfterRename = 3
}

object Baz3 extends {
  override val NameAfterRename = 1
} with Baz

object Test {
  Baz2.NameAfterRename
  Baz3.NameAfterRename
}