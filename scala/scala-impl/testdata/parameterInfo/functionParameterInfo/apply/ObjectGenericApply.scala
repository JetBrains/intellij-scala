object ObjectGenericApply2 {
  def apply[T](x: T) = 1
}

ObjectGenericApply2[Int](<caret>)
//TEXT: [T](x: Int), STRIKEOUT: false