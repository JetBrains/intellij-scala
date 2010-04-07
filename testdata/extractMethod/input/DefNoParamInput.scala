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
  def testMethodName(goo: () => Int): Int = {
    val x = goo()
    val y = goo()
    x
  }

  def foo {
    def goo: Int = 23

    /*start*/
    val x: Int = testMethodName(goo _)
    /*end*/
    x + goo
  }
}
*/