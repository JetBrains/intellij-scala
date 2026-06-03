class AddRepeatedParams {
  def foo(i: Int, b: Boolean, xs: Int*): Int = i

  foo(i = 1, b = true, 1)
  this foo(2, false, 1)
}

class AddRepeatedParamsTest extends AddRepeatedParams {
  override def foo(i: Int, b: Boolean, xs: Int*): Int = super.foo(i = 1, b = false, 1)
}