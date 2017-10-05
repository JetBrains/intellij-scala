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
  def foo[T](x: T) {
    def goo[G](y: G) {
      val z: T = x

      val u: T = testMethodName(x, y, z)

      u
    }
    goo(3)
  }

  def testMethodName[T, G](x: T, y: G, z: T): T = {
    y: G
    z: T
    val u = x
    u
  }
}
*/