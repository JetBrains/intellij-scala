trait DifferentParamNames {
  def foo(newName: Int): Int
}

class Child extends DifferentParamNames {
  override def foo(number: Int): Int = number
}