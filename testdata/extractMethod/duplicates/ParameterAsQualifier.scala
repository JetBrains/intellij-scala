object ParameterAsQualifier {
  def foo(first: String, second: String) {
    val i = 0
    /*start*/first.charAt(i).isUpper/*end*/ || second.charAt(1).isUpper
  }
}
/*
object ParameterAsQualifier {
  def foo(first: String, second: String) {
    val i = 0
    /*start*/testMethodName(first, i)/*end*/ || testMethodName(second, 1)
  }

  def testMethodName(first: String, i: Int): Boolean = {
    first.charAt(i).isUpper
  }
}
*/