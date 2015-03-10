object SeveralDuplicates {
  def foo(i: Int) {
    /*start*/
    println(i + 1)
    /*end*/
    println(2 + 1)
  }

  def bar() {
    println(3 + 1)
  }

  println(4 + 1)
}
/*
object SeveralDuplicates {
  def foo(i: Int) {
    /*start*/
    testMethodName(i)
    /*end*/
    testMethodName(2)
  }

  def testMethodName(i: Int): Unit = {
    println(i + 1)
  }

  def bar() {
    testMethodName(3)
  }

  testMethodName(4)
}
*/