object InfixEmptyParams {
  def foo() = ()

  def bar[A](a: A) = a

  def over() = "over()"

  def over[A](a: A) = "over[A](a: A)"

}

InfixEmptyParams/*resolved: true*/foo ()
InfixEmptyParams/*resolved: true*/bar ()
InfixEmptyParams./*resolved: true*/bar()

InfixEmptyParams/*resolved: true, line: 6*/over ()
