class GenericTest extends Generic[String, String] {

  override def foo(t: String, s: String): String = super.foo(t, )

  foo("1", )

  this.foo("", )
}