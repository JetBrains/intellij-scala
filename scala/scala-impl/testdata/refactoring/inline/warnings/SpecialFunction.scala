//> expected.error cannot.inline.special.function
object Foo {
  def /*caret*/apply(i: Int): String = i.toString

  Foo(42)
}

