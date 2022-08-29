package tests

object Child1 extends Base {
  override val /*caret*/foo = 1

  val x = new Base {
    override def /*caret*/foo() = 3
  }
  x.foo/*caret*/()
}

class Child2 extends {
  override val /*caret*/foo = 1
} with Base {
  /*caret*/foo
}

class Child3 extends Base {
  override def /*caret*/foo() = 2

  foo/*caret*/()

  Child1./*caret*/foo
}