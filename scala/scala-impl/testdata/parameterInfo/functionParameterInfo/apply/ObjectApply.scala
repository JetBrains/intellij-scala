object ObjectApply {
  def apply(x: Int) = x
  def apply(x: Double, y: Int) = x/y
}

ObjectApply(<caret>)
/*
TEXT: x: Double, y: Int, STRIKEOUT: false
TEXT: x: Int, STRIKEOUT: false
*/