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
  def foo {
    def goo(): Int = 435


    val f: Int = testMethodName(goo _)

    f
  }

  def testMethodName(goo: () => Int): Int = {
    val g = goo()
    val f = goo()
    f
  }
}
*/