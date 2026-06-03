class AddNewClause {
  def <caret>foo(x: Int) = {}

  foo(1)
  this.foo(1)
}

class AddNewClause2 extends AddNewClause {
  override def foo(x: Int): Unit = {
    super.foo(x)
  }
}