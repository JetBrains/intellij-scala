case class ScalaConstructor(x: Int) {
  def foo = 2
}

new ScalaConstructor(<caret>)
//TEXT: x: Int, STRIKEOUT: false