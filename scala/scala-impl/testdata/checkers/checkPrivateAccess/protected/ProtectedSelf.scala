object ProtectedSelf {
  trait A {
    self: B =>
    self./*ref*/goo
  }
  class B {
    protected def goo: Int = 45
  }
}
//true