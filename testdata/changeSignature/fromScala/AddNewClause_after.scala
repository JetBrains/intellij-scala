class AddNewClause {
  def foo(b: Boolean)(x: Int, y: Int) = {}

  foo(true)(1, 0)
  this.foo(true)(1, 0)
}

class AddNewClause2 extends AddNewClause {
  override def foo(b: Boolean)(x: Int, y: Int): Unit = {
    super.foo(true)(x, 0)
  }
}