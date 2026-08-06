package tests

trait ScalaTrait extends JavaBase {
  override def foo = 1
}

class ScalaClass extends ScalaTrait {
  override def foo = 2
}
