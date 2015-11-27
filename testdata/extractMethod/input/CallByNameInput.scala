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

    testMethodName(x)

  }

  def testMethodName(x: => Int): Unit = {
    x + 44
  }
}
*/