object IfStatementSimple {
  def foo(x: Boolean => String) = x(false)

  foo(/*start*/if (_) "" else "g"/*end*/)
}
//(Boolean) => String