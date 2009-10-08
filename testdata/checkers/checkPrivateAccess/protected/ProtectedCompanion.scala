class ProtectedCompanion {
  class A {
    protected def foo = 55
  }

  class B extends A {

  }

  object B {
    val b = new B
    b./*ref*/foo
  }
}
//true