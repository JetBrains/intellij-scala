class AnonymousFunctions {
  def <caret>foo(i: Int, j: Int, b: Boolean): Int = 1

  Seq(1).fold(0)(foo(_, _, true))
  Seq(1).map(foo(1, _, true))

  val fun = foo _
}