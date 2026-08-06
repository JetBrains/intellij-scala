class DefaultParameterInNextClause {
  def foo(x: Int)(y: Int = /* resolved: true */x) = x + y
}