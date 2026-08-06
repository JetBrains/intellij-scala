//case class
class UnitReturnSeveralOutput2 {
  def foo(i: Int) {
/*start*/
    if (true) return
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
class UnitReturnSeveralOutput2 {
  def foo(i: Int) {

    val testMethodNameResult: TestMethodNameResult = testMethodName match {
      case Some(result) => result
      case None => return
    }
    val TestMethodNameResult(x: Int, y: String, z: Int, zz: String) = testMethodNameResult

    println(x + y + z + zz)
  }

  case class TestMethodNameResult(x: Int, y: String, z: Int, zz: String)

  def testMethodName: Option[TestMethodNameResult] = {
    if (true) return None
    val x = 0
    val y = "a"
    val z = 1
    val zz = "1"
    Some(TestMethodNameResult(x, y, z, zz))
  }
}
*/