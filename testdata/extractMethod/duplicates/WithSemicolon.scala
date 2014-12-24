object WithSemicolon {
  def foo(i: Int) {
    /*start*/

    val y = 0
    println(i)
    y + 1
    /*end*/
  }

  def foofoo() {
    val y = 0
    println(1);
    y + 1
  }
}
/*
object WithSemicolon {
  def foo(i: Int) {
    /*start*/

    testMethodName(i)
    /*end*/
  }

  def testMethodName(i: Int): Unit = {
    val y = 0
    println(i)
    y + 1
  }

  def foofoo() {
    testMethodName(1)

  }
}
*/