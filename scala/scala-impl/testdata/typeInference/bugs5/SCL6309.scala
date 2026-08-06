object A {
  class B
  class C
  implicit def a2b(a: Any): B = new B
  implicit def i2c(i: Int): C = new C

  def l(x: B): B = x
  def l(x: C): C = x

  /*start*/l(1)/*end*/
}
//A.C