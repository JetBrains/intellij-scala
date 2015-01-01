object WithDefinition {
  def foo(i: Int) {
    /*start*/

    def bar = {
      val y = 0
      y + 1
    }
    println(bar + i)
    /*end*/
  }

  def foofoo() {
    def baz = {
      val x = 0
      x + 1
    }
    println(baz + 1)
  }
}
/*
object WithDefinition {
  def foo(i: Int) {
    /*start*/

    testMethodName(i)
    /*end*/
  }

  def testMethodName(i: Int): Unit = {
    def bar = {
      val y = 0
      y + 1
    }
    println(bar + i)
  }

  def foofoo() {
    testMethodName(1)
  }
}
*/