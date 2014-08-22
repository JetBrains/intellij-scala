class ArrayToVarargsTest extends ArrayToVarargs {
  override def foo(i: Int, js: Array[Int], b: Boolean): Unit = super.foo(i, js, b)

  foo(1, Array(2, 2), true)
  val xs = Array(3, 3)
  foo(1, xs, true)
}