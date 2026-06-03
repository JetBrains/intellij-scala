package tests

abstract class B extends A {
  override def f/*caret*/(): Unit = {}
}

class C extends B {
  override def f/*caret*/(): Unit = {}
}
