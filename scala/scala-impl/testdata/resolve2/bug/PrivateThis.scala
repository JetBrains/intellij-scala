class PrivateThis {
  object A {
    private[this] val foo = 56
  }
  class A {
    A./* accessible: false */foo
  }
}
