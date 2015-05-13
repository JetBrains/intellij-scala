class NoDefaultArg {
  def foo(i: Int, j: Int =  ) {}

  foo(1)
}

class NoDefaultArgChild extends NoDefaultArg {
  override def foo(i: Int, j: Int) = super.foo(i)
}