class ProtectedThis {
  class A {
    protected[this] val a = 34
  }

  class B extends A {
    val g = a
  }

  object B {
    val b = new B
    b./* resolved: false */a
  }
}