object ObjectApply {
  def apply(x: Int) = x
  def apply(x: Double, y: Int) = x/y
}

ObjectApply(<caret>)
/*
x: Double, y: Int
x: Int
*/