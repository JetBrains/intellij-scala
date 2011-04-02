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
  def testMethodName(x: => Int) {
    x + 77
  }

  def foo {
    lazy val x = 44
    /*start*/
    testMethodName(x)
    /*end*/
  }
}
*/