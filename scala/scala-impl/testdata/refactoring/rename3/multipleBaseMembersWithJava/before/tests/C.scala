package tests

class C extends A with B {
  override def f/*caret*/(): Unit = {}
}
