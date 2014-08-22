class ArrayToVarargsTest extends ArrayToVarargs {
  override def foo(i: Int, b: Boolean, js: Int*): Unit = super.foo(i, b, js: _*)

  foo(1, true, 2, 2)
  val xs = Array(3, 3)
  foo(1, true, xs: _*)
}