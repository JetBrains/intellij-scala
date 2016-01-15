class ReturnNoOutput {
  def foo(i: Int): Int = {
/*start*/
    if (true) return i
    println(i)
/*end*/
    println()
    42
  }
}
/*
class ReturnNoOutput {
  def foo(i: Int): Int = {

    testMethodName(i) match {
      case Some(toReturn) => return toReturn
      case None =>
    }

    println()
    42
  }

  def testMethodName(i: Int): Option[Int] = {
    if (true) return Some(i)
    println(i)
    None
  }
}
*/