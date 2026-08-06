class SelfInvocation(x: Int, y: Int) {
  def this(s: String) {
    this(1, 2)
  }

  def this(x: Int) {
    this(<caret>)
  }

  def this(b: Boolean) {
    this("text")
  }
}
/*
TEXT: s: String, STRIKEOUT: false
TEXT: x: Int, y: Int, STRIKEOUT: false
*/