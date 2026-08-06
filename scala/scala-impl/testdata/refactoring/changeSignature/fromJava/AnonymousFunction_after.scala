class AnonymousFunctionScala {
  Seq(1).map((i: Int) => AnonymousFunction.foo(i, 0))
  Seq(1).map((i: Int) => AnonymousFunction.foo(i, 0))
  Seq(1).map((i: Int) => AnonymousFunction.foo(i, 0))
  Seq(1).map((i: Int) => AnonymousFunction.foo(i, 0))

  val fun = (i: Int) => AnonymousFunction.foo(i, 0)
}