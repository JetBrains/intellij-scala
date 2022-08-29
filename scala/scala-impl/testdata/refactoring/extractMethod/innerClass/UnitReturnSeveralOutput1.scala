//inner class
class UnitReturnSeveralOutput1 {
  def foo(i: Int) {
/*start*/
    if (true) return
    var x = 0
    var y = "a"
    var z = 1
    val zz = "1"
/*end*/
    println(x + y + z + zz)
  }
}
/*
//inner class
class UnitReturnSeveralOutput1 {
  def foo(i: Int) {

    val testMethodNameResult: TestMethodNameResult = testMethodName match {
      case Some(result) => result
      case None => return
    }
    var x: Int = testMethodNameResult.x
    var y: String = testMethodNameResult.y
    var z: Int = testMethodNameResult.z
    val zz: String = testMethodNameResult.zz

    println(x + y + z + zz)
  }

  class TestMethodNameResult(val x: Int, val y: String, val z: Int, val zz: String)

  def testMethodName: Option[TestMethodNameResult] = {
    if (true) return None
    var x = 0
    var y = "a"
    var z = 1
    val zz = "1"
    Some(new TestMethodNameResult(x, y, z, zz))
  }
}
*/