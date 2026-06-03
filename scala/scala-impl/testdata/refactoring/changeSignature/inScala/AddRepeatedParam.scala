class AddRepeatedParams {
  def <caret>foo(i: Int, b: Boolean): Int = i

  foo(b = true, i = 1)
  this foo (2, false)
}

class AddRepeatedParamsTest extends AddRepeatedParams {
  override def foo(i: Int, b: Boolean) = super.foo(b = false, i = 1)
}