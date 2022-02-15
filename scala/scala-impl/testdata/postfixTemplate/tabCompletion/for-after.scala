package tests

object Example {
  def foo(list: List[Integer]) {
    for (elem <- list) {<caret>}
  }
}
