class ClassApply {
  def apply(x: Int) = 1
}

val t = new ClassApply
t(<caret>)
//TEXT: x: Int, STRIKEOUT: false