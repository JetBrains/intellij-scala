object MethodCall {
  def foo(x: (String, String) => String) = x("1", "2")

  foo(/*start*/_.concat(_)/*end*/)
}
//(String, String) => String