class ByName {
  def foo(x: Int, s: => AnyRef): Unit = {
    x
    s
  }

  foo(1, "")
}

class ByNameChild extends ByName {
  override def foo(x: Int, s: => AnyRef): Unit = super.foo(x, s)
}