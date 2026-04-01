package tests

trait A {
  def NameAfterRename(): Unit
}

abstract class B extends A {
  override def NameAfterRename(): Unit = {}
}

class C extends B {
  override def NameAfterRename(): Unit = {}
}
