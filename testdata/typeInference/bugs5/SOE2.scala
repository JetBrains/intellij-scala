object A {
  class C

  val z = new C {
    type B = B
  }

  def foo(x: C {type B = String}) = 1

  /*start*/foo(z)/*end*/
}
//Int