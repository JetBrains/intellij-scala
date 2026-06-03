class GenericUpdate[T] {
  def update(x: T, y: T) = 33
  def apply(x: Boolean)
}

val x = new GenericUpdate[Int]
x(<caret>)
/*
TEXT: x: Boolean, STRIKEOUT: false
TEXT: x: Int, STRIKEOUT: false
*/