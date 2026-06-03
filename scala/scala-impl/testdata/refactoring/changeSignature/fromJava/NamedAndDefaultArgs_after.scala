class NamedAndDefaultArgsTest extends NamedAndDefaultArgs {
  override def foo(s: String = "aaa", b: Boolean, b2: Boolean): Unit = super.foo(s, b, true)

  foo(b = true, b2 = true)
  this foo(s = "bbb", b = true, true)
  this foo(b = true, b2 = true)
  foo(b = true, b2 = true)

  super.foo("", true, true)
}