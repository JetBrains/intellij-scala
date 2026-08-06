class ClassInput {
  def foo {
    class A
    val g: A = new A
    /*start*/
    g
    /*end*/
  }
}
/*
class ClassInput {
  def foo {
    class A
    val g: A = new A

    def testMethodName: Unit = {
      g
    }
    testMethodName

  }
}
*/