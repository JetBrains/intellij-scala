class Property {
  def foo(implicit x: Int) = 1
}

val y = new Property
y.foo<caret>
//TEXT: implicit x: Int, STRIKEOUT: false