class ArrayToVarargsTest extends ArrayToVarargs {
  override def foo(i: Int, b: Boolean, js: Int*): Unit = super.foo(i, b, js: _*)

  foo(1, true, 2, 2)
  val xs = Array(3, 3)
  foo(1, true, xs: _*)
  foo(i = 1, b = false, 1, 2)
  foo(1, b = true, js = xs: _*)
}