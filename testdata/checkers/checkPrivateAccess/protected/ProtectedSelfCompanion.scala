object ProtectedSelfCompanion {
  trait A {
    self: B =>
    B.goo
  }
  class B
  object B {
    protected def goo: Int = 45
  }
}
//false