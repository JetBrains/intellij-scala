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


    val j: Int = testMethodName


    val i = j
  }

  def testMethodName: Int = {
    val j = 77
    j
  }
}
*/