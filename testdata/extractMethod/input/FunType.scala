class FunType {
  def a = {
    val y = (_: Int) + 1
    /*start*/
    val z = y
    y(1)
    /*end*/
    z
  }
}
/*
class FunType {
  def testMethodName(y: (Int) => Int): (Int) => Int = {
    val z = y
    y(1)
    z
  }

  def a = {
    val y = (_: Int) + 1
    /*start*/
    val z: (Int) => Int = testMethodName(y)
    /*end*/
    z
  }
}
*/