class CallByNameInput {
  def foo(x: => Int) {
    /*start*/
    x + 44
    /*end*/
  }
}
/*
class CallByNameInput {
  def foo(x: => Int) {
    /*start*/
    testMethodName(x)
    /*end*/
  }

  def testMethodName(x: => Int): Unit = {
    x + 44
  }
}
*/