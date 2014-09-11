class VarargsTest extends Varargs {
  override def foo(i: Int, b: Boolean, strs: String*) {}

  super.foo(1, true)
  super.foo(1, true, "2")
  super.foo(1, true, "2", "3")

  foo(1, true)
  foo(1, true, "2")
  foo(1, true, "2", "3")

  foo(i = 1, true, "2", "3")
  foo(i = 1, true, "2")
  this foo (i = 1, true, "2", "3")
}
