package tests

trait ScalaTrait1 extends JavaBase {
  override def NameAfterRename = 1
}

trait ScalaTrait2 extends ScalaTrait1

class ScalaClass extends ScalaTrait2 {
  override def NameAfterRename = 2
}
