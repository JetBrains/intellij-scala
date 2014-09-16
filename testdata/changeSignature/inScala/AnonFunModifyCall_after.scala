class AnonymousFunctions {
  def foo(i: Int, j: Int): Int = 1

  Seq(1).map((i: Int) => foo(i, 0))
  Seq(1).map((i: Int) => foo(i, 0))
  Seq(1).map((i: Int) => foo(i, 0))
  Seq(1).map((i: Int) => this.foo(i, 0))

  val fun = (i: Int) => foo(i, 0)
}