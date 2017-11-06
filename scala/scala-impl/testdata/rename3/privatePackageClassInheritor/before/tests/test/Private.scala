package tests.test

private[test] trait Private {
  def /*caret*/foo = ???
}

class Public extends Private
