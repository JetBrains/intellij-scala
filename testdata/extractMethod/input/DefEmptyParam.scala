class DefEmptyParam {
  def foo {
    def goo(): Int = 435

    /*start*/
    val g = goo
    val f = goo()
    /*end*/
    f
  }
}
/*
class DefEmptyParam {
  def testMethodName(goo: () => Int): Int = {
    val g = goo()
    val f = goo()
    f
  }

  def foo {
    def goo(): Int = 435

    /*start*/
    val f: Int = testMethodName(goo _)
    /*end*/
    f
  }
}
*/