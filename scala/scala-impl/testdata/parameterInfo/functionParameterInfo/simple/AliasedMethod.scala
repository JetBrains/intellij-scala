object test {
  def foo(x: Int, y: Int) = new Object
}

object test2 {
  import test.{foo => bar}

  val x = bar(<caret>)
}
//TEXT: x: Int, y: Int, STRIKEOUT: false