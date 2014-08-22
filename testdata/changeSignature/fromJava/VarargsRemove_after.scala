class VarargsTest extends VarargsRemove {
  override def foo(i: Int, b: Boolean) {}

  super.foo(1, true)
  super.foo(1, true)
  super.foo(1, true)

  foo(1, true)
  foo(1, true)
  foo(1, true)
}
