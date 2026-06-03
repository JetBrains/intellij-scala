object A {
  def foo = /*start*/42 + 239/*end*/

}
/*
object A {
  def foo = /*start*/testMethodName/*end*/

  def testMethodName: Int = {
    42 + 239
  }
}
*/