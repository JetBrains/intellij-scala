class A extends B

trait T extends  G{
  self: B =>
  self.foo
}

trait G {
  def foo: Int = 56
}

class B