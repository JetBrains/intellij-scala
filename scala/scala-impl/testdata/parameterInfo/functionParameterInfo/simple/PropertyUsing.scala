class Property {
  def foo(using x: Int) = 1
}

val y = new Property
y.fo<caret>o
//TEXT: implicit x: Int, STRIKEOUT: false