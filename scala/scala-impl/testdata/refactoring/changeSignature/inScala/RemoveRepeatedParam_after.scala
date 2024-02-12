class RemoveRepeatedParams {
  def foo(i: Int, b: Boolean): Int = i

  foo(i = 1, b = true)
  this foo(2, false)
  foo(1, false)
}

class RemoveRepeatedParamsTest extends RemoveRepeatedParams {
  override def foo(i: Int, b: Boolean): Int = super.foo(i = 1, b = false)

  foo(1, false)
  foo(1, true)
}