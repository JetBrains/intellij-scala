object LiteralParameter {
  def foo(i: 1) {
/*start*/
    println(i + 1)
/*end*/
    println(2 + 1)
  }
}
/*
object LiteralParameter {
  def foo(i: 1) {

    testMethodName(i)

    println(2 + 1)
  }

  def testMethodName(i: 1): Unit = {
    println(i + 1)
  }
}
*/