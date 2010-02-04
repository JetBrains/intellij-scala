class A {
  private def f = {}
}

class B extends A {
  /* resolved: false */ f
}