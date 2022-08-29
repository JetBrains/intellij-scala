class VarargsTest extends VarargsRemove {
  override def foo(i: Int, strs: String*) = {}

  super.foo(1)
  super.foo(1, "2")
  super.foo(1, "2", "3")

  foo(1)
  foo(1, "2")
  foo(1, "2", "3")

  foo(i = 1, "2", "3")
  foo(strs = "2", i = 1)
}
