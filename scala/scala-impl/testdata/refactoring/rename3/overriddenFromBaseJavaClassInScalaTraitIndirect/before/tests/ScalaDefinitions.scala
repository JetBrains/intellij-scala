package tests

trait ScalaTrait1 extends JavaBase {
  override def foo = 1
}

trait ScalaTrait2 extends ScalaTrait1

class ScalaClass extends ScalaTrait2 {
  override def foo = 2
}
