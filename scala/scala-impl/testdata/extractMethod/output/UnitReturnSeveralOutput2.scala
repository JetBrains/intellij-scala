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
class UnitReturnSeveralOutput2 {
  def foo(i: Int) {

    val (x: Int, y: String, z: Int, zz: String) = testMethodName match {
      case Some(result) => result
      case None => return
    }

    println(x + y + z + zz)
  }

  def testMethodName: Option[(Int, String, Int, String)] = {
    if (true) return None
    val x = 0
    val y = "a"
    val z = 1
    val zz = "1"
    Some((x, y, z, zz))
  }
}
*/