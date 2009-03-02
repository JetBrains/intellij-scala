object ObjectGenericApply {
  def apply[T](x: T) = 1
}

apply[Int](/*caret*/)