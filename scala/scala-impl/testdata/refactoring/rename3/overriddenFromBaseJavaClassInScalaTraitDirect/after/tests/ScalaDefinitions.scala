package tests

trait ScalaTrait extends JavaBase {
  override def NameAfterRename = 1
}

class ScalaClass extends ScalaTrait {
  override def NameAfterRename = 2
}
