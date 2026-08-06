class ChangeLocalVar {
  def foo {
    var i = 23
/*start*/
    i = 24
/*end*/
    val j = i
  }
}
/*
class ChangeLocalVar {
  def foo {
    var i = 23

    def testMethodName(): Unit = {
      i = 24
    }

    testMethodName()

    val j = i
  }
}
*/