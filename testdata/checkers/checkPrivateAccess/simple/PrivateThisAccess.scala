class PrivateThisAccess {
  object A {
    private[this] val foo = 56
  }
  class A {
    A./*ref*/foo
  }
}
//false