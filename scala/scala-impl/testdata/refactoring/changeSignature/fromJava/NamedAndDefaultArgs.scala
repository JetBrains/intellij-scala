class NamedAndDefaultArgsTest extends NamedAndDefaultArgs {
  override def foo(i: Int = 1, j: Int, s: String = "aaa", b: Boolean) = super.foo(i, j, s, b)

  foo(j = 1, b = true)
  this foo (0, 1, b = true, s = "bbb")
  this foo (1, j = 1, b = true)
  foo(1, 2, b = true)

  super.foo(1, 2, "", true)
}