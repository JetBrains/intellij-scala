class Simple {
  def addOne(x: Int) = x + 1
}

val y = new Simple
y.addOne(<caret>)
//TEXT: x: Int, STRIKEOUT: false