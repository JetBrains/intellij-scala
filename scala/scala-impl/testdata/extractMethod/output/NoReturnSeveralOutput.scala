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
class NoReturnSeveralOutput {
  def foo(i: Int) {


    val (x: Int, y: String, z: Int, zz: String) = testMethodName

    println(x + y + z + zz)
  }

  def testMethodName: (Int, String, Int, String) = {
    val x = 0
    val y = "a"
    val z = 1
    val zz = "1"
    (x, y, z, zz)
  }
}
*/