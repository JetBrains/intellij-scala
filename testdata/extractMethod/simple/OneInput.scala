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

    testMethodName(i)

  }

  def testMethodName(i: Int): Unit = {
    i * i
  }
}
*/