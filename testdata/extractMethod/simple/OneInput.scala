class OneInput {
  def foo {
    val i = 34
    /*start*/
    i * i
    /*end*/
  }
}
/*
class OneInput {
  def foo {
    val i = 34
    /*start*/
    testMethodName(i)
    /*end*/
  }

  def testMethodName(i: Int): Unit = {
    i * i
  }
}
*/