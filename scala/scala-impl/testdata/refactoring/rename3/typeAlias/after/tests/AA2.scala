package tests

object AA2 extends AA {
  override type NameAfterRename = this.type
}

object AA3 extends A {
  override type NameAfterRename = this.type

  val x: NameAfterRename = this
}


