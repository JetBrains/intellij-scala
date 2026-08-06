class AnonymousFunctionScala {
  Seq(1).map(AnonymousFunction.foo(_))
  Seq(1).map(AnonymousFunction.foo _)
  Seq(1).map(AnonymousFunction.foo)
  Seq(1).map(AnonymousFunction foo _)

  val fun = AnonymousFunction.foo _
}