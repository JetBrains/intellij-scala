object InstanceMethodScala {
  def test = {
    val value = new InstanceMethod
    value.foo(1)

    value.foo _
  }
}