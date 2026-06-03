package tests

object Baz {
  var bar/*caret*/ = 0
}

object Test {
  def foo() = {
    Baz./*caret*/bar = 1
    Baz./*caret*/bar_=(2)
    Baz.bar/*caret*/_$eq(3)
    Baz.bar/*caret*/.toString
  }
}