class AddRepeatedParams {
  def foo(i: Int, xs: Int*): Int = i

  foo(i = 1)
  this foo (2)
}

class AddRepeatedParamsTest extends AddRepeatedParams {
  override def foo(i: Int, xs: Int*): Int = super.foo(i = 1)
}