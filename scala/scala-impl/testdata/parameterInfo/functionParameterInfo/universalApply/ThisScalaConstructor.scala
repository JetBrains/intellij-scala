class ThisScalaConstructor {
  def this(x: Int) {
    this()
  }
  def this(x: Boolean) {
    this()
  }
}

ThisScalaConstructor(<caret>)
/*
TEXT: <no parameters>, STRIKEOUT: false
TEXT: x: Boolean, STRIKEOUT: false
TEXT: x: Int, STRIKEOUT: false
*/