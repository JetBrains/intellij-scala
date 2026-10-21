object LiteralExpectedType {
  def foo: 3 = /*start*/1 + 2/*end*/
}
/*
object LiteralExpectedType {
  def foo: 3 = testMethodName

  def testMethodName: 3 = {
    1 + 2
  }
}
*/