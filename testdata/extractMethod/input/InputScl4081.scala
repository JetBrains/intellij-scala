class InputScl4081 {
  def foo {
    val validations = List(0)
    val bar = 1
    val as = /*start*/validations.size + bar/*end*/
    as
  }
}
/*
class InputScl4081 {
  def foo {
    val validations = List(0)
    val bar = 1
    val as = /*start*/testMethodName(bar, validations)/*end*/
    as
  }

  def testMethodName(bar: Int, validations: List[Int]): Int = {
    validations.size + bar
  }
}
*/