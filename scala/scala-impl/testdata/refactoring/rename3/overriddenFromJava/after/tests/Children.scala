package tests

object Child1 extends Base {
  override val NameAfterRename = 1

  val x = new Base {
    override def NameAfterRename() = 3
  }
  x.NameAfterRename()
}

class Child2 extends {
  override val NameAfterRename = 1
} with Base {
  NameAfterRename
}

class Child3 extends Base {
  override def NameAfterRename() = 2

  NameAfterRename()

  Child1.NameAfterRename
}