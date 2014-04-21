class ReturnSeveralOutput2 {
  def foo(i: Int): Int = {
    /*start*/

    val x = i
    if (true) return x
    val y = "a"
    var z = 1
    val zz = "1"
    /*end*/
    println(x + y + z + zz)
    i
  }
}
/*
class ReturnSeveralOutput2 {
  def foo(i: Int): Int = {
    /*start*/

    val tupleResult: (Int, String, Int, String) = testMethodName(i) match {
      case Left(toReturn) => return toReturn
      case Right(result) => result
    }
    val x: Int = tupleResult._1
    val y: String = tupleResult._2
    var z: Int = tupleResult._3
    val zz: String = tupleResult._4
    /*end*/
    println(x + y + z + zz)
    i
  }

  def testMethodName(i: Int): Either[Int, (Int, String, Int, String)] = {
    val x = i
    if (true) return Left(x)
    val y = "a"
    var z = 1
    val zz = "1"
    Right((x, y, z, zz))
  }
}
*/