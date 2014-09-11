class RemoveRepeatedParams {
  def <caret>foo(i: Int, b: Boolean, xs: Int*): Int = i

  foo(i = 1, b = true, 1, 2, 3)
  this foo (2, false)
  foo(1, false, 1)
}

class RemoveRepeatedParamsTest extends RemoveRepeatedParams {
  override def foo(i: Int, b: Boolean, xs: Int*) = super.foo(i = 1, b = false)

  foo(1, false)
  foo(1, true, 1, 2)
}