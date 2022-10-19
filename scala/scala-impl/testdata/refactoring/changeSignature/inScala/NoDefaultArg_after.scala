class NoDefaultArg {
  def foo(i: Int, j: Int =): Unit = {}

  foo(1)
}

class NoDefaultArgChild extends NoDefaultArg {
  override def foo(i: Int, j: Int): Unit = super.foo(i)
}