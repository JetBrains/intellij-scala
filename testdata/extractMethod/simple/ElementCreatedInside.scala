class A {
  def foo {
    /*start*/

    val j = 77
    /*end*/

    val i = j
  }
}
/*
class A {
  def foo {
    /*start*/

    val j: Int = testMethodName
    /*end*/

    val i = j
  }

  def testMethodName: Int = {
    val j = 77
    j
  }
}
*/