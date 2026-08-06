
trait X[T] {
  def testMethod(x:Int)
}

trait Y[T] {
  def z: T = sys.exit()
  def anotherTestMethod(x:Int)
}

trait Z {
  def zTest(x:Int)
}
trait SelfHighlightingBug1 {
  self: X[_] with Y[_] =>

  def test {
    val x: Y[_] = null

    val g = z
    val u = x.z
    /*start*/(g, u)/*end*/
    testMethod(0)
    anotherTestMethod(0)
  }
}

trait SelfHighlightingBug2 {
  self: Z with X[_] =>
  testMethod(0)
}
//(Any, Any)