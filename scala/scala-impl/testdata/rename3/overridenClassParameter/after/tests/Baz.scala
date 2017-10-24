package tests

class Baz(val NameAfterRename: Int = 0) {}

object Baz2 extends Baz(NameAfterRename = 2) {
  override val NameAfterRename = 3
}

object Baz3 extends {
  override val NameAfterRename = 1
} with Baz

class Baz4(override val NameAfterRename: Int = 1) extends Baz

object Test {
  Baz2.NameAfterRename
  Baz3.NameAfterRename
}