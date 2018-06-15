class ScalaConstructor(protected override val x: Int) {
  def foo = 2
}

new ScalaConstructor(<caret>)
//x: Int