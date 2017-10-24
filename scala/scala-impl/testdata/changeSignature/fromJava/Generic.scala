class GenericTest extends Generic[String, String] {

  override def foo(t: String): Unit = super.foo(t)

  foo("1")

  this foo ""
}