//inner class
class NoReturnSeveralOutput {
  def foo(i: Int) {
    /*start*/

    val x = 0
    val y = "a"
    val z = 1
    val zz = "1"
    /*end*/
    println(x + y + z + zz)
  }
}
/*
//inner class
class NoReturnSeveralOutput {
  def foo(i: Int) {


    val testMethodNameResult: TestMethodNameResult = testMethodName
    val x: Int = testMethodNameResult.x
    val y: String = testMethodNameResult.y
    val z: Int = testMethodNameResult.z
    val zz: String = testMethodNameResult.zz

    println(x + y + z + zz)
  }

  class TestMethodNameResult(val x: Int, val y: String, val z: Int, val zz: String)

  def testMethodName = {
    val x = 0
    val y = "a"
    val z = 1
    val zz = "1"
    new TestMethodNameResult(x, y, z, zz)
  }
}
*/