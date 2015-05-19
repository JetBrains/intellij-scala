class AddNewClauseWithDefault {
  def foo(b: Boolean = true)(x: Int, y: Int = 0) = {}

  foo()(1)
  this.foo()(1)
}

class AddNewClauseWithDefault2 extends AddNewClauseWithDefault {
  override def foo(b: Boolean)(x: Int, y: Int): Unit = {
    super.foo()(x)
  }
}