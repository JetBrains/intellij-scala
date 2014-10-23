class Test {
  def test(param: String): PartialFunction[String, String] = {
    case x: String => /*start*/test2(x) + " Goodbye"/*end*/
  }

  def test2(param: String): Unit = {
    "Hello" + param
  }
}
/*
class Test {
  def test(param: String): PartialFunction[String, String] = {
    case x: String => /*start*/testMethodName(x)/*end*/
  }

  def testMethodName(x: String): String = {
    test2(x) + " Goodbye"
  }

  def test2(param: String): Unit = {
    "Hello" + param
  }
}
*/