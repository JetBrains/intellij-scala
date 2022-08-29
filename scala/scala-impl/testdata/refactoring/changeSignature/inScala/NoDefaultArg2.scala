class NoDefaultArg {
  def <caret>foo(i: Int) = {}

  foo(1)
}

class NoDefaultArgChild extends NoDefaultArg {
  override def foo(i: Int) = super.foo(i)
}