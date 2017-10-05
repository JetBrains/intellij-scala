package tests

class AA extends A {
  override type NameAfterRename = this.type
}

trait A {
  type NameAfterRename
}