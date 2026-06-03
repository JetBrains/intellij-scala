class ScalaConstructor(protected override val x: Int) {
  def foo = 2
}

new ScalaConstructor(<caret>)
//TEXT: x: Int, STRIKEOUT: false