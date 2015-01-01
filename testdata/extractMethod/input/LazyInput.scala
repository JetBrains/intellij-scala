class LazyInput {
  def foo {
    lazy val x = 44
    /*start*/
    x + 77
    /*end*/
  }
}
/*
class LazyInput {
  def foo {
    lazy val x = 44
    /*start*/
    testMethodName(x)
    /*end*/
  }

  def testMethodName(x: => Int): Unit = {
    x + 77
  }
}
*/