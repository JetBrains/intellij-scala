object SelfType {
  trait A {
    def foo = 1
  }
  class B extends A with C
  trait C {
    self: B =>
    override def <caret>foo = 2
  }
}