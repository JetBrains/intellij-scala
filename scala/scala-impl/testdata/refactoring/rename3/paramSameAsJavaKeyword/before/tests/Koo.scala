object Koo {
  def foo(switch/*caret*/: String): Unit = {
    println(switch)
  }

  foo(switch/*caret*/ = "")
}
