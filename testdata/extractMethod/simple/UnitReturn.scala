class UnitReturn {
  def foo {
    var i = 23
    /*start*/

    {1; ()}
    /*end*/
    val j = i
  }
}
/*
class UnitReturn {
  def foo {
    var i = 23
    /*start*/

    testMethodName
    /*end*/
    val j = i
  }

  def testMethodName: Unit = {
    1;
    ()
  }
}
*/