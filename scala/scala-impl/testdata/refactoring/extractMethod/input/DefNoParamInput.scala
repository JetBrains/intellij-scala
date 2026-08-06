class DefNoParamInput {
  def foo {
    def goo: Int = 23

/*start*/

    val x = goo
    val y = goo
/*end*/
    x + goo
  }
}
/*
class DefNoParamInput {
  def foo {
    def goo: Int = 23


    val x: Int = testMethodName(goo _)

    x + goo
  }

  def testMethodName(goo: () => Int): Int = {
    val x = goo()
    val y = goo()
    x
  }
}
*/