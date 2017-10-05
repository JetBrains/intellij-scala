class NoReturnOneOutput {
  def foo(i: Int): Int = {
/*start*/
    if (true) {}
    val x = 0
    println(i)
/*end*/
    println()
    x
  }
}
/*
class NoReturnOneOutput {
  def foo(i: Int): Int = {

    val x: Int = testMethodName(i)

    println()
    x
  }

  def testMethodName(i: Int): Int = {
    if (true) {}
    val x = 0
    println(i)
    x
  }
}
*/