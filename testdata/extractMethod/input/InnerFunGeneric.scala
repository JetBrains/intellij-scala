class A {
  def foo[T](x: T) {
    def goo[G](y: G) {
      val z: T = x
      /*start*/
      y: G
      z: T
      val u = x
      /*end*/
      u
    }
    goo(3)
  }
}
/*
class A {
  def testMethodName[T, G](y: G, z: T, x: T): T = {
    y: G
    z: T
    val u = x
    u
  }

  def foo[T](x: T) {
    def goo[G](y: G) {
      val z: T = x
      /*start*/
      val u: T = testMethodName(y, z, x)
      /*end*/
      u
    }
    goo(3)
  }
}
*/