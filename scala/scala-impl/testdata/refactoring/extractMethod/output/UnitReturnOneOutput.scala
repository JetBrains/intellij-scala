class UnitReturnOneOutput {
  def foo(i: Int) {
/*start*/
    if (true) return
    val x = 0
    println(i)
/*end*/
    println(x)
  }
}
/*
class UnitReturnOneOutput {
  def foo(i: Int) {

    val x: Int = testMethodName(i) match {
      case Some(result) => result
      case None => return
    }

    println(x)
  }

  def testMethodName(i: Int): Option[Int] = {
    if (true) return None
    val x = 0
    println(i)
    Some(x)
  }
}
*/