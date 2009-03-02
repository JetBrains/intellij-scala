object ObjectGenericApply {
  def apply[T](x: T) = 1
}

ObjectGenericApply[Int](/*caret*/)