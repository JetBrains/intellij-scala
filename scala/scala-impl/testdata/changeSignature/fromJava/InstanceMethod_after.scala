object InstanceMethodScala {
  def test = {
    val value = new InstanceMethod
    value.bar(true, 1)

    (i: Int) => value.foo(true, i)
  }
}