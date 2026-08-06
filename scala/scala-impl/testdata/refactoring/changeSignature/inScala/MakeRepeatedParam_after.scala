class AddRepeatedParams {
  def foo(i: Int, b: Boolean*): Int = i

  foo(i = 1, true)
  this foo(2, false)
}

class AddRepeatedParamsTest extends AddRepeatedParams {
  override def foo(i: Int, b: Boolean*): Int = super.foo(i = 1, false)
}