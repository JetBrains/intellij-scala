package A.B.C

private class Foo

class FooWithParam(val num: Int = 5)

private class Bar {
  def getFoo(): Foo = {
    new FooWithParam
    new FooWithParam(4)
    new FooWithParam(num = 8)

    new A.B.C.Foo
    new Foo
  }

  private class BarInner
}