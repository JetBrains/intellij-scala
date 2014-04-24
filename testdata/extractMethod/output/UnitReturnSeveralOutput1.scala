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
class UnitReturnSeveralOutput1 {
  def foo(i: Int) {
    /*start*/
    val tupleResult: (Int, String, Int, String) = testMethodName match {
      case Some(result) => result
      case None => return
    }
    var x: Int = tupleResult._1
    var y: String = tupleResult._2
    var z: Int = tupleResult._3
    val zz: String = tupleResult._4
    /*end*/
    println(x + y + z + zz)
  }

  def testMethodName: Option[(Int, String, Int, String)] = {
    if (true) return None
    var x = 0
    var y = "a"
    var z = 1
    val zz = "1"
    Some((x, y, z, zz))
  }
}
*/