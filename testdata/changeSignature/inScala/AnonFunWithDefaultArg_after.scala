class AnonymousFunctions {
  def foo(i: Int, j: Int = 0): Int = 1

  Seq(1).map((i: Int) => foo(i))
  Seq(1).map((i: Int) => foo(i))
  Seq(1).map((i: Int) => foo(i))
  Seq(1).map((i: Int) => this.foo(i))

  val fun = (i: Int) => foo(i)
}