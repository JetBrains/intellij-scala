class PrivateThis {
  object A {
    private[this] val foo = 56
  }
  class A {
    A./* resolved: false */foo
  }
}
