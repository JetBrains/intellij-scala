trait DifferentParamNames {
  def <caret>foo(i: Int): Int
}

class Child extends DifferentParamNames {
  override def foo(number: Int): Int = number
}