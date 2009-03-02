class ClassGenericApply {
  def apply[T](x: T) = 1
  def apply(x: Int) = 2
}

val y = new ClassGenericApply
y[Double](/*caret*/5)