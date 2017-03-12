class GenericClassApply[T] {
  def apply(x: T) = 1
}

val y = new GenericClassApply[Int]
y(<caret>)
//x: Int