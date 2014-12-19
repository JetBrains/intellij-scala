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
    /*start*/
    testMethodName(i)
    /*end*/
    println()
  }

  def testMethodName(i: Int): Unit = {
    if (true) {}
    println(i)
  }
}
*/