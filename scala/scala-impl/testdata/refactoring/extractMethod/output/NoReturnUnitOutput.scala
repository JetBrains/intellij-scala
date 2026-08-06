class NoReturnUnitOutput {
  def foo(i: Int) {
/*start*/

    val x: Unit = println("unit")
    println(i)
/*end*/
    x
  }
}
/*
class NoReturnUnitOutput {
  def foo(i: Int) {


    val x: Unit = testMethodName(i)

    x
  }

  def testMethodName(i: Int): Unit = {
    val x: Unit = println("unit")
    println(i)
    x
  }
}
*/