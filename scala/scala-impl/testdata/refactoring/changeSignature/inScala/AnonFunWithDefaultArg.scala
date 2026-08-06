class AnonymousFunctions {
  def <caret>foo(i: Int): Int = 1

  Seq(1).map(foo(_))
  Seq(1).map(foo _)
  Seq(1).map(foo)
  Seq(1).map(this foo _)

  val fun = foo _
}