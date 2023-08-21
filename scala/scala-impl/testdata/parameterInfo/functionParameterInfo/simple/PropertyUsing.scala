class Property {
  def foo(using x: Int) = 1
}

val y = new Property
y.fo<caret>o
//TEXT: using x: Int, STRIKEOUT: false