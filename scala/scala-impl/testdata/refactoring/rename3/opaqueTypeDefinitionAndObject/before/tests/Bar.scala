package tests

opaque type /*caret*/Bar = Int

object /*caret*/Bar {
  def apply(i: Int): Bar = i
  extension (b: /*caret*/Bar) def value: Int = b
}
