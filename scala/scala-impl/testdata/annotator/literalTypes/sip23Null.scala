object Test {
  def bar(x: Any): x.type = null
  def foo(y: AnyVal): y.type = null
  def baz(z: AnyRef): z.type = null // ok
}
