object A {
  def foo = /*start*/42 + 239/*end*/
}
/*
object A {
  def testMethodName: Int = {
    42 + 239
  }

  def foo = /*start*/testMethodName/*end*/
}
*/