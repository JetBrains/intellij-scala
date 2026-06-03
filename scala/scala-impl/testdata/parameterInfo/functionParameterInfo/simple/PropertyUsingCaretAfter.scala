class Property {
  def foo(using x: Int) = 1
}

val y = new Property
y.foo<caret>
//TEXT: using x: Int, STRIKEOUT: false