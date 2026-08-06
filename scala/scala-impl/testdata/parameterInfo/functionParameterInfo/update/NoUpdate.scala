class NoUpdate {
  def update(x: Int) {}

  def apply(x: Int) = 33
}

val x = new NoUpdate
2 + x(<caret>)
//TEXT: x: Int, STRIKEOUT: false