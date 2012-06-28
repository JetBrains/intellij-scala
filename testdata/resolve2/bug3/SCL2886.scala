object SCL2886 {
  trait B extends C with D {
    type X
  }

  trait C {
    self:  B =>

    def foo(x: X)

    def foo(x: Int)
  }
  trait D {
    self:  B =>
    val x: X = sys.exit()
    /* line: 9 */foo(x)
  }
}