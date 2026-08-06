class SimpleProtected {
  class A {
    protected def foo = 34
  }

  class B extends A {
    /*ref*/foo
  }
}
//true