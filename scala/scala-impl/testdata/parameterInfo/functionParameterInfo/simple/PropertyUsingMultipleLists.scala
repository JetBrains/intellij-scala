class Property {
  def foo(using x: Int)(using y: String) = 1
}

val y = new Property
y.fo<caret>o
//TEXT: (using x: Int)(using y: String), STRIKEOUT: false