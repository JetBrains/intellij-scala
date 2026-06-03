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


    val testMethodNameResult: (Int, String, Int, String) = testMethodName(i) match {
      case Left(toReturn) => return toReturn
      case Right(result) => result
    }
    val x: Int = testMethodNameResult._1
    val y: String = testMethodNameResult._2
    var z: Int = testMethodNameResult._3
    val zz: String = testMethodNameResult._4

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