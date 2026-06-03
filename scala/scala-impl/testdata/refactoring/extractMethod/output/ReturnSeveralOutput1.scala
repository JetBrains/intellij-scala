class ReturnSeveralOutput1 {
  def foo(i: Int): Int = {
/*start*/

    val x = i
    if (true) return x
    val y = "a"
    val z = 1
    val zz = "1"
/*end*/
    println(x + y + z + zz)
    i
  }
}
/*
class ReturnSeveralOutput1 {
  def foo(i: Int): Int = {


    val (x: Int, y: String, z: Int, zz: String) = testMethodName(i) match {
      case Left(toReturn) => return toReturn
      case Right(result) => result
    }

    println(x + y + z + zz)
    i
  }

  def testMethodName(i: Int): Either[Int, (Int, String, Int, String)] = {
    val x = i
    if (true) return Left(x)
    val y = "a"
    val z = 1
    val zz = "1"
    Right((x, y, z, zz))
  }
}
*/