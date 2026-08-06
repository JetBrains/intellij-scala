class SimpleMethodScala {
  def foo(i: Int, s: AnyRef, b: Boolean): Unit = {
    i
  }
}

class SimpleMethodChild extends SimpleMethodScala {
  override def foo(i: Int, s: AnyRef, b: Boolean): Unit = {
    i
    super.foo(i, "hi", true)
  }
}