class AddNewClauseWithDefault {
  def <caret>foo(x: Int) = {}

  foo(1)
  this.foo(1)
}

class AddNewClauseWithDefault2 extends AddNewClauseWithDefault {
  override def foo(x: Int): Unit = {
    super.foo(x)
  }
}