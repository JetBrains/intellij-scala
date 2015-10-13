trait A {
  type R
}
class B {
  type R = Unit
}
class C extends B with A {
  def z(x: R) = 1
  def z(i: Int, j: Int) = "text"
  /*start*/z(1)/*end*/
}
//Int