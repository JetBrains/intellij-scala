def foo(bar: Int) {
  val validations = List(1,2,3,2)
  val test = /*start*/validations.size + bar/*end*/  //apply extract method refactoring to this line
  println(test / 2)
}
/*
def testMethodName(bar: Int, validations: List[Int]): Int = {
  validations.size + bar
}
def foo(bar: Int) {
  val validations = List(1,2,3,2)
  val test = testMethodName(bar, validations)  //apply extract method refactoring to this line
  println(test / 2)
}
*/