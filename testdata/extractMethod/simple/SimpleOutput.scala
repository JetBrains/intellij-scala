class SimpleOutput {
  def foo {
    var i = 23
    /*start*/
    i = 24
    /*end*/
    val j = i
  }
}
/*
class SimpleOutput {
  def testMethodName(_i: Int): Int = {
    var i: Int = _i
    i = 24
    i
  }

  def foo {
    var i = 23
    /*start*/
    i = testMethodName(i)
    /*end*/
    val j = i
  }
}
*/