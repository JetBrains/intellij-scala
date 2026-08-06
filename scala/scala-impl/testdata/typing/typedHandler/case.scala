class TestClass {
  def foo(arg1: String, arg2: Int = 42, arg3: Option[String]) = arg3 match {
    case Some(_) => true
      <caret>
  }
}
-----
class TestClass {
  def foo(arg1: String, arg2: Int = 42, arg3: Option[String]) = arg3 match {
    case Some(_) => true
    case _
  }
}