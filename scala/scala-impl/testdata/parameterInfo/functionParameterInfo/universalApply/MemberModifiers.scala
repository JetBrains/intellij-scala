class ScalaConstructor(protected override val x: Int) {
  def foo = 2
}

ScalaConstructor(<caret>)
//TEXT: x: Int, STRIKEOUT: false