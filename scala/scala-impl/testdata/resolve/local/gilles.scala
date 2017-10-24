class OuterTest {
  trait InnerSuper {
    def iii: Int = 5
  }

  trait TestTree {
    def fooo = 42
  }

  def f: Int = 4

  trait I extends InnerSuper {
    is: TestTree =>
    this.<ref>iii + this.fooo
  }
}