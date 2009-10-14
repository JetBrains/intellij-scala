object IfStatementComplex {
  def foo(x: Boolean => String => String) = x(false)("45")

  foo(if (_) /*start*/_.concat("3")/*end*/ else _.concat("6"))
}
//(String) => String