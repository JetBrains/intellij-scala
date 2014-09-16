class AnonymousFunctions {
  def foo(j: Int, b: Boolean, s: AnyRef = ""): Int = 1

  Seq(1).fold(0)((i: Int, j: Int) => foo(j, true))
  Seq(1).map((j: Int) => foo(j, true))

  val fun = (i: Int, j: Int, b: Boolean) => foo(j, b)
}