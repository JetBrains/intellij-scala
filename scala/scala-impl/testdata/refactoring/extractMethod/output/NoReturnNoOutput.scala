class NoReturnNoOutput {
  def foo(i: Int) {
/*start*/
    if (true) {}
    println(i)
/*end*/
    println()
  }
}
/*
class NoReturnNoOutput {
  def foo(i: Int) {

    testMethodName(i)

    println()
  }

  def testMethodName(i: Int): Unit = {
    if (true) {}
    println(i)
  }
}
*/