class Property {
  def foo(implicit x: Int) = 1
}

val y = new Property
y.fo<caret>o
//TEXT: implicit x: Int, STRIKEOUT: false