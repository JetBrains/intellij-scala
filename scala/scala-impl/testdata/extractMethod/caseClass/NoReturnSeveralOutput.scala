//case class
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
//case class
class NoReturnSeveralOutput {
  def foo(i: Int) {


    val testMethodNameResult: TestMethodNameResult = testMethodName
    val TestMethodNameResult(x: Int, y: String, z: Int, zz: String) = testMethodNameResult

    println(x + y + z + zz)
  }

  case class TestMethodNameResult(x: Int, y: String, z: Int, zz: String)

  def testMethodName = {
    val x = 0
    val y = "a"
    val z = 1
    val zz = "1"
    TestMethodNameResult(x, y, z, zz)
  }
}
*/