class SimpleMethodScala {
  def <caret>foo(i: Int, s: String, b: Boolean): Unit = {
    i
  }
}

class SimpleMethodChild extends SimpleMethodScala {
  override def foo(i: Int, s: String, b: Boolean): Unit = {
    i
    super.foo(i, s, true)
  }
}